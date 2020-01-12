package com.airxiechao.axcboot.communication.rpc.client;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.common.IRpcMessageHandler;
import com.airxiechao.axcboot.communication.rpc.common.RpcFuture;
import com.airxiechao.axcboot.communication.rpc.common.RpcMessage;
import com.airxiechao.axcboot.communication.rpc.common.RpcContext;
import com.alibaba.fastjson.JSON;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Sharable
public class RpcClientMessageRouter extends ChannelInboundHandlerAdapter {

    private final static Logger logger = LoggerFactory.getLogger(RpcClientMessageRouter.class);

    private Map<String, IRpcMessageHandler> serviceHandlers;
    private ThreadPoolExecutor executor;
    private RpcContext rpcContext = new RpcContext();
    private Map<String, RpcFuture> pendingClients = new ConcurrentHashMap<>();
    private RpcClient client;

    public RpcClientMessageRouter(Map<String, IRpcMessageHandler> serviceHandlers, int numWorkerThreads, RpcClient client){
        this.client = client;
        this.serviceHandlers = serviceHandlers;

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1000);
        ThreadFactory factory = new ThreadFactory() {

            AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("rpc-client["+client.getName()+"]-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }

        };
        this.executor = new ThreadPoolExecutor(numWorkerThreads, numWorkerThreads * 100, 30, TimeUnit.SECONDS,
                queue, factory, new ThreadPoolExecutor.CallerRunsPolicy());

    }

    public RpcContext getRpcContext(){
        return rpcContext;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        updateRpcContext(ctx, new Date());

        client.heartbeatOnce();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        close();

        pendingClients.forEach((__, future) -> {
            future.fail(new Exception("rpc-client-["+client.getName()+"] connection not active error"));
        });
        pendingClients.clear();

        // 尝试重连
        ctx.channel().eventLoop().schedule(() -> {
            client.connect();
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof RpcMessage)) {
            return;
        }

        RpcMessage message = (RpcMessage) msg;

        if(message.getType().endsWith("_response")){
            this.handleClientMessage(ctx, message);
        }else{
            this.executor.execute(() -> {
                this.handleServiceMessage(ctx, message);
            });
        }
    }

    private void handleClientMessage(ChannelHandlerContext ctx, RpcMessage message){
        RpcFuture future = pendingClients.remove(message.getRequestId());
        if (future == null) {
            logger.error("future not found with type {}", message.getType());
            return;
        }

        try{
            Response response = JSON.parseObject(message.getPayload(), Response.class);
            future.success(response);
        }catch (Exception e){
            logger.error("handle response message error", e);
            future.fail(e);
        }
    }

    private void handleServiceMessage(ChannelHandlerContext ctx, RpcMessage message){
        IRpcMessageHandler handler = serviceHandlers.get(message.getType());
        Response response;
        if(null != handler){
            String payload = message.getPayload();
            Map payloadMap = JSON.parseObject(payload, Map.class);
            try {
                response = handler.handle(ctx, payloadMap);
            }catch (Exception e){
                logger.error("handle service [{}] error", message.getType(), e);

                response = new Response();
                response.error(e.getMessage());
            }
        }else{
            response = new Response();
            response.error("no service [" + message.getType() + "]");
        }

        ctx.writeAndFlush(new RpcMessage(message.getRequestId(), message.getType()+"_response", JSON.toJSONString(response)));
    }

    public RpcFuture sendToServer(RpcMessage message) {
        ChannelHandlerContext ctx = null;
        if(!rpcContext.isHeartbeatExpired()){
            ctx = rpcContext.getContext();
        }
        RpcFuture future = new RpcFuture();
        ChannelHandlerContext ctx1 = ctx;
        if (ctx != null) {
            ctx.channel().eventLoop().execute(() -> {
                pendingClients.put(message.getRequestId(), future);
                ctx1.writeAndFlush(message);
            });
        } else {
            future.fail(new Exception("rpc-client-["+client.getName()+"] connection not active error"));
        }
        return future;
    }

    public void updateRpcContext(ChannelHandlerContext ctx, Date lastHeartbeatTime){
        rpcContext.setContext(ctx);
        updateRpcContextLastHeartbeatTime(lastHeartbeatTime);
    }

    public void updateRpcContextLastHeartbeatTime(Date lastHeartbeatTime){
        if(null != lastHeartbeatTime){
            rpcContext.setLastHeartbeatTime(lastHeartbeatTime);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("connection error", cause);
    }

    public void close() {
        ChannelHandlerContext ctx = rpcContext.getContext();
        if (ctx != null) {
            ctx.close();
        }

        updateRpcContext(null, null);
        client.setConnected(false);
    }
}
