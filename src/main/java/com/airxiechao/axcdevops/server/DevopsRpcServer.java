package com.airxiechao.axcdevops.server;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.server.RpcServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class DevopsRpcServer {

    private RpcServer rpcServer;

    public DevopsRpcServer(int port){
        rpcServer = new RpcServer("devops", "0.0.0.0", port, 2, 10);

        initServices();

        enterShell();
    }

    private void initServices(){

    }

    public void start(){
        rpcServer.start();
    }

    public void enterShell(){

        Thread t = new Thread(()->{
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while(true){
                System.out.print("Enter your command: ");
                String command = null;
                try {
                    command = br.readLine();
                    parseShellCommand(command);
                } catch (IOException e) {

                }
            }
        });
        t.setDaemon(true);
        t.start();

    }

    private void parseShellCommand(String command){
        String[] lines = command.strip().split(" ");
        if(lines.length < 1){
            return;
        }
        String client = lines[0];

        String type;
        if(lines.length == 1 && "ls".equals(lines[0])){
            type = client;
        }else{
            if(lines.length > 1){
                type = lines[1];
            }else{
                return;
            }
        }

        switch(type){
            case "ls":
                System.out.println(rpcServer.getActiveClients());
                break;
            case "isactive":
                Optional<String> opt = rpcServer.getActiveClients().stream().filter(c->client.equals(c)).findFirst();
                if(opt.isPresent()){
                    System.out.println("["+client+"]在线");
                }else{
                    System.out.println("["+client+"]离线");
                }
                break;
            case "download":
                if(lines.length < 5){
                    break;
                }
                String url = lines[2];
                String dir = lines[3];
                String fileName = lines[4];

                System.out.println("向["+client+"]发送下载命令["+url+"]...");

                Map params = new HashMap<>();
                params.put("url", url);
                params.put("dir", dir);
                params.put("fileName", fileName);
                try{
                    Response resp = rpcServer.sendToClient(client, type, params);
                    if(resp.isSuccess()){
                        System.out.println("下载完成");
                    }else{
                        System.out.println("下载发生错误："+resp.getMessage());
                    }
                }catch (Exception e){
                    System.out.println("下载发生错误："+e.getMessage());
                }

                break;
            case "execute":
                if(lines.length < 3){
                    break;
                }
                StringBuilder sb = new StringBuilder();
                for(int i = 2; i < lines.length; ++i){
                    sb.append(lines[i] + " ");
                }
                String cmd = sb.toString().strip();

                System.out.println("向["+client+"]发送执行命令["+cmd+"]...");

                Map params1 = new HashMap<>();
                params1.put("cmd", cmd);
                try{
                    Response resp = rpcServer.sendToClient(client, type, params1);
                    if(resp.isSuccess()){
                        String output = URLDecoder.decode((String)resp.getData(), "UTF-8");
                        System.out.println(
                                "执行完成：\n" +
                                "============================================>>>\n"+
                                output+"\n" +
                                "============================================<<<");
                    }else{
                        System.out.println("执行发生错误："+resp.getMessage());
                    }
                }catch (Exception e){
                    System.out.println("执行发生错误："+e.getMessage());
                }

                break;
            default:
                System.out.println("没有命令["+type+"]");
                break;
        }
    }
}
