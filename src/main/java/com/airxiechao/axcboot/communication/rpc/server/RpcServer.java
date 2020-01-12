package com.airxiechao.axcboot.communication.rpc.server;

import com.airxiechao.axcboot.communication.common.RequestId;
import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.common.*;
import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private String name;
    private String serverIp;
    private int serverPort;
    private int numIoThreads;
    private int numWorkerThreads;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup serverGroup;
    private Channel serverChannel;
    private Map<String, IRpcMessageHandler> serviceHandlers = new HashMap<>();
    private RpcServerMessageRouter router;

    public RpcServer(String name, String ip, int port, int numIoThreads, int numWorkerThreads){

        this.name = name;
        this.serverIp = ip;
        this.serverPort = port;
        this.numIoThreads = numIoThreads;
        this.numWorkerThreads = numWorkerThreads;
    }

    public String getName(){
        return this.name;
    }

    public List<String> getActiveClients(){
        return router.getActiveClients();
    }

    public void start(){

        registerHeartbeatService();

        serverBootstrap = new ServerBootstrap();
        serverGroup = new NioEventLoopGroup(this.numIoThreads);
        serverBootstrap.group(serverGroup);
        RpcMessageEncoder encoder = new RpcMessageEncoder();
        router = new RpcServerMessageRouter(serviceHandlers, this.numWorkerThreads, this);
        serverBootstrap.channel(NioServerSocketChannel.class).childHandler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipe = socketChannel.pipeline();
                        pipe.addLast(new ReadTimeoutHandler(RpcContext.HEARTBEAT_PERIOD_SECS * 2));
                        pipe.addLast(new RpcMessageDecoder());
                        pipe.addLast(encoder);
                        pipe.addLast(router);
                    }
                }
        );

        serverChannel = serverBootstrap.bind(this.serverIp, this.serverPort).channel();
        logger.info("rpc-server-[{}] has started at {}:{}", this.name, this.serverIp, this.serverPort);
    }

    public void stop(){
        serverChannel.close();
        serverGroup.shutdownGracefully();
        router.closeGracefully();
    }

    public RpcServer registerService(String type, IRpcMessageHandler handler){
        serviceHandlers.put(type, handler);
        return this;
    }

    private void registerHeartbeatService(){
        registerService("ping", (ctx, payload) -> {
            String clientName = (String)payload.get("name");

            logger.info("rpc-server-[{}] receives heartbeat from [{}]", this.name, clientName);
            router.updateRpcContext(clientName, ctx, new Date());

            Response resp = new Response();
            resp.success();

            return resp;
        });
    }

    public Response sendToClient(String client, String type, Map payload){
        return sendToClient(client, type, JSON.toJSONString(payload));
    }

    public Response sendToClient(String client, String type, String payload){
        try{
            String requestId = RequestId.next();
            RpcMessage message = new RpcMessage(requestId, type, payload);
            RpcFuture future = this.router.sendToClient(client, message);
            return future.get();
        }catch (Exception e) {
            throw new RpcException(e);
        }
    }

}
