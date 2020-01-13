package com.airxiechao.axcboot.communication.rpc.server;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.common.*;
import com.alibaba.fastjson.JSON;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.airxiechao.axcboot.communication.rpc.util.RpcUtil.buildResponseType;

@Sharable
public class RpcServerMessageRouter extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RpcServerMessageRouter.class);

    private Map<String, IRpcMessageHandler> serviceHandlers;
    private ThreadPoolExecutor executor;
    private Map<String, RpcContext> contexts = new ConcurrentHashMap<>();
    private Map<String, RpcClientFuture> pendingRequests = new ConcurrentHashMap<>();
    private RpcServer rpcServer;

    public RpcServerMessageRouter(Map<String, IRpcMessageHandler> serviceHandlers, int numWorkerThreads, RpcServer rpcServer){
        this.serviceHandlers = serviceHandlers;
        this.rpcServer = rpcServer;

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1000);
        ThreadFactory factory = new ThreadFactory() {

            AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("rpc-server-["+rpcServer.getName()+"]-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }

        };
        this.executor = new ThreadPoolExecutor(numWorkerThreads, numWorkerThreads * 100, 30, TimeUnit.SECONDS,
                queue, factory, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public List<String> getActiveClients(){
        List<String> clients = new ArrayList<>();
        for(Map.Entry<String, RpcContext> entry : contexts.entrySet()){
            String name = entry.getKey();
            RpcContext context = entry.getValue();

            if(!context.isHeartbeatExpired()){
                clients.add(name);
            }
        }
        return clients;
    }

    /**
     * 获取客户端
     * @param ctx
     * @return
     */
    private String getClientByContext(ChannelHandlerContext ctx){
        Optional<String> opt = contexts.entrySet().stream()
                .filter(c->ctx.equals(c.getValue().getContext()))
                .map(c->c.getKey())
                .findFirst();

        String client = null;
        if(opt.isPresent()) {
            client = opt.get();
        }

        return client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        String client = getClientByContext(ctx);

        logger.info("[{}] inactive", client);

        if(null != client){
            closeClient(client);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception{
        if (!(msg instanceof RpcMessage)) {
            return;
        }

        RpcMessage message = (RpcMessage) msg;

        if(message.isResponse()){
            this.handleResponseMessage(ctx, message);
        }else{
            this.executor.execute(() -> {
                this.handleServiceMessage(ctx, message);
            });
        }
    }

    /**
     * 处理响应消息
     * @param ctx
     * @param message
     */
    private void handleResponseMessage(ChannelHandlerContext ctx, RpcMessage message) throws Exception {
        RpcClientFuture clientFuture = pendingRequests.remove(message.getRequestId());
        if (clientFuture == null) {
            logger.error("future not found with type {}", message.getType());
            return;
        }

        try{
            Response response = JSON.parseObject(message.getPayload(), Response.class);
            clientFuture.success(response);
        }catch (Exception e){
            logger.error("parse response message error", e);
            clientFuture.fail(e);

            throw new Exception("parse response message error", e);
        }
    }

    /**
     * 处理请求消息
     * @param ctx
     * @param message
     */
    private void handleServiceMessage(ChannelHandlerContext ctx, RpcMessage message){
        IRpcMessageHandler handler = serviceHandlers.get(message.getType());
        Response response;
        if(null != handler){
            try {
                String payload = message.getPayload();
                Map payloadMap = JSON.parseObject(payload, Map.class);
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

        ctx.writeAndFlush(new RpcMessage(message.getRequestId(), buildResponseType(message.getType()), JSON.toJSONString(response)));
    }

    public void updateRpcContext(String name, ChannelHandlerContext ctx, Date date){
        RpcContext rpcContext = contexts.get(name);
        if(null == rpcContext){
            rpcContext = new RpcContext();
        }

        rpcContext.setContext(ctx);
        if(null != date){
            rpcContext.setLastHeartbeatTime(date);
        }

        this.contexts.put(name, rpcContext);
    }

    public RpcFuture sendToClient(String client, RpcMessage message) {

        ChannelHandlerContext ctx = null;
        RpcContext rpcContext = this.contexts.get(client);
        if(null != rpcContext){
            if(!rpcContext.isHeartbeatExpired()){
                ctx = rpcContext.getContext();
            }
        }

        RpcClientFuture clientFuture = new RpcClientFuture();
        ChannelHandlerContext ctx1 = ctx;
        if (ctx1 != null) {

            clientFuture.setClientName(client);
            ctx1.channel().eventLoop().execute(() -> {
                pendingRequests.put(message.getRequestId(), clientFuture);
                ctx1.writeAndFlush(message);
            });
        } else {
            clientFuture.fail(new Exception("rpc-client-["+client+"] connection not active"));
        }
        return clientFuture;
    }

    public void closeGracefully() {
        this.executor.shutdown();
        try {
            this.executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        this.executor.shutdownNow();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();

        String client = getClientByContext(ctx);
        logger.error("close [{}] connection by uncaught error", client, cause);
    }

    /**
     * 关闭客户端连接
     * @param client
     */
    private void closeClient(String client){
        List<String> futureKeys = pendingRequests.entrySet().stream()
                .filter(c->client.equals(c.getValue().getClientName()))
                .map(c->c.getKey())
                .collect(Collectors.toList());

        for(String key : futureKeys){
            RpcClientFuture future = pendingRequests.get(key);
            future.fail(new Exception("rpc-client-["+client+"] connection not active error"));
            pendingRequests.remove(key);
        }

        updateRpcContext(client, null, null);
    }
}
