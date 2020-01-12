package com.airxiechao.axcboot.communication.rpc.client;

import com.airxiechao.axcboot.communication.common.RequestId;
import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.common.*;
import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static com.airxiechao.axcboot.communication.rpc.common.RpcContext.HEARTBEAT_PERIOD_SECS;

public class RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    private String name;
    private String serverIp;
    private int serverPort;
    private int numIoThreads;
    private int numWorkerThreads;
    private Bootstrap clientBootstrap;
    private NioEventLoopGroup clientGroup;
    private Map<String, IRpcMessageHandler> serviceHandlers = new HashMap<>();
    private RpcClientMessageRouter router;
    private boolean connected;
    private RpcFuture connectedFuture;
    private boolean stopped;

    private ScheduledExecutorService scheduledExecutorService;

    public RpcClient(String name, String ip, int port, int numWorkerThreads){

        this.name = name;
        this.serverIp = ip;
        this.serverPort = port;
        this.numIoThreads = 1;
        this.numWorkerThreads = numWorkerThreads;

        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder()
                .setNameFormat("rpc-client["+this.name+"]-schedule")
                .setDaemon(true)
                .build()
        );

        init();
        //connect();
    }

    public String getName(){
        return this.name;
    }

    public void setConnected(boolean connected){
        this.connected = connected;
    }

    private void init(){

        initHeartbeat();

        clientBootstrap = new Bootstrap();
        clientGroup = new NioEventLoopGroup(this.numIoThreads);
        clientBootstrap.group(clientGroup);
        RpcMessageEncoder encoder = new RpcMessageEncoder();
        router = new RpcClientMessageRouter(serviceHandlers, this.numWorkerThreads, this);
        clientBootstrap.channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipe = ch.pipeline();
                pipe.addLast(new ReadTimeoutHandler(HEARTBEAT_PERIOD_SECS * 2));
                pipe.addLast(new RpcMessageDecoder());
                pipe.addLast(encoder);
                pipe.addLast(router);
            }

        });
    }

    private void initHeartbeat(){
        this.scheduledExecutorService.scheduleAtFixedRate(()->{
            heartbeat();
        }, HEARTBEAT_PERIOD_SECS, HEARTBEAT_PERIOD_SECS, TimeUnit.SECONDS);
    }

    private void heartbeat(){
        try{
            Map params = new HashMap();
            params.put("name", this.name);
            Response resp = sendToServer("ping", params);
            if(resp.isSuccess()){
                router.updateRpcContextLastHeartbeatTime(new Date());
                logger.info("rpc-client-[{}] heartbeat success", this.name);
            }else{
                logger.error("rpc-client-[{}] heartbeat error [{}]", this.name, resp.getMessage());
            }
        }catch (Exception e){
            logger.error("rpc-client-[{}] heartbeat error [{}]", this.name, e.getMessage());
        }
    }

    public void heartbeatOnce(){
        Thread t = new Thread(()-> heartbeat());
        t.setDaemon(true);
        t.start();
    }

    public RpcClient connect() {
        if (stopped) {
            return this;
        }

        if(connected){
            return this;
        }

        if(null != router.getRpcContext().getContext()){
            return this;
        }

        connectedFuture = new RpcFuture();
        clientBootstrap.connect(serverIp, serverPort).addListener(future -> {
            if (future.isSuccess()) {
                connected = true;
                connectedFuture.success(new Response());

                logger.info("rpc-client-[{}] connecting to server {}:{} success", name, serverIp, serverPort);

                return;
            }else{
                connectedFuture.fail(future.cause());
            }

            if (!stopped) {
                clientGroup.schedule(() -> {
                    connect();
                }, 1, TimeUnit.SECONDS);
            }
            logger.error("rpc-client-[{}] connecting to server {}:{} fails [{}]", name, serverIp, serverPort, future.cause().getMessage());
        });

        return this;
    }

    public RpcClient awaitConnected(){

        while(!stopped){

            try {
                Response resp = connectedFuture.get();
                if(resp.isSuccess()){
                    return this;
                }
            } catch (Exception e) {

            }
        }

        return this;
    }



    public RpcClient registerService(String type, IRpcMessageHandler handler){
        serviceHandlers.put(type, handler);
        return this;
    }

    public Response sendToServer(String type, Map payload){
        return sendToServer(type, JSON.toJSONString(payload));
    }

    public Response sendToServer(String type, String payload){
        try{
            String requestId = RequestId.next();
            RpcMessage message = new RpcMessage(requestId, type, payload);
            RpcFuture future = this.router.sendToServer(message);
            return future.get();
        }catch (Exception e) {
            throw new RpcException(e);
        }
    }

    public void disconnect() {
        stopped = true;
        router.close();
        clientGroup.shutdownGracefully(0, 5000, TimeUnit.SECONDS);
    }

}
