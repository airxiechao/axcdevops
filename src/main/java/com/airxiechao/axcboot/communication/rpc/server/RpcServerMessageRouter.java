package com.airxiechao.axcboot.communication.rpc.server;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.client.RpcClient;
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

@Sharable
public class RpcServerMessageRouter extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RpcServerMessageRouter.class);

    private Map<String, IRpcMessageHandler> serviceHandlers;
    private ThreadPoolExecutor executor;
    private Map<String, RpcContext> contexts = new ConcurrentHashMap<>();
    private Map<String, RpcClientFuture> pendingClients = new ConcurrentHashMap<>();
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

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Optional<String> opt = contexts.entrySet().stream()
                .filter(c->ctx.equals(c.getValue().getContext()))
                .map(c->c.getKey())
                .findFirst();

        if(opt.isPresent()){
            String client = opt.get();

            List<String> futureKeys = pendingClients.entrySet().stream()
                    .filter(c->client.equals(c.getValue().getClientName()))
                    .map(c->c.getKey())
                    .collect(Collectors.toList());

            for(String key : futureKeys){
                RpcClientFuture future = pendingClients.get(key);
                future.fail(new Exception("rpc-client-["+client+"] connection not active error"));
                pendingClients.remove(key);
            }

            updateRpcContext(client, null, null);
        }
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
        RpcClientFuture clientFuture = pendingClients.remove(message.getRequestId());
        if (clientFuture == null) {
            logger.error("future not found with type {}", message.getType());
            return;
        }

        try{
            Response response = JSON.parseObject(message.getPayload(), Response.class);
            clientFuture.success(response);
        }catch (Exception e){
            logger.error("handle response message error", e);
            clientFuture.fail(e);
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
                pendingClients.put(message.getRequestId(), clientFuture);
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
        logger.error("connection error", cause);
    }
}
