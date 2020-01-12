package com.airxiechao;

import com.airxiechao.axcboot.util.ConfigUtil;
import com.airxiechao.axcdevops.client.DevopsRpcClient;
import com.airxiechao.axcdevops.server.DevopsRpcServer;

public class App
{
    public static void main( String[] args )
    {
        if(args.length < 1){
            return;
        }

        String mode = args[0];
        switch (mode){
            case "server":
                runAsServer();
                break;
            case "client":
                runAsClient();
                break;
            default:
                runAsClient();
                break;
        }
    }

    /**
     * 运行客户端
     */
    private static void runAsClient(){
        DevopsRpcClient devopsRpcClient = new DevopsRpcClient(getRpcServerIp(), getRpcServerPort());
        devopsRpcClient.connect(false);
    }

    /**
     * 运行服务端
     */
    private static void runAsServer(){
        DevopsRpcServer devopsRpcServer = new DevopsRpcServer(getRpcServerPort());
        devopsRpcServer.start();


    }


    private static String getRpcServerIp(){
        String ip = ConfigUtil.getConfig().getString("rpc-server-ip");
        return ip;
    }

    private static int getRpcServerPort(){
        int port = ConfigUtil.getConfig().getInt("rpc-server-port");
        return port;
    }
}
