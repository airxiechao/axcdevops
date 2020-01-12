package com.airxiechao.axcdevops.client;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.rpc.client.RpcClient;
import com.airxiechao.axcboot.util.ConfigUtil;
import com.airxiechao.axcdevops.util.CmdUtil;
import com.airxiechao.axcdevops.util.HttpUtil;
import com.google.common.primitives.Chars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public class DevopsRpcClient {

    private static final Logger logger = LoggerFactory.getLogger(DevopsRpcClient.class);

    private RpcClient rpcClient;

    public DevopsRpcClient(String ip, int port){
        String clientName = ConfigUtil.getConfig().getString("rpc-client-name");
        rpcClient = new RpcClient(clientName, ip, port, 10);

        initServices();
    }

    private void initServices(){
        // downlaod
        rpcClient.registerService("download", (ctx, payload) -> {
            String url = (String)payload.get("url");
            String dir = (String)payload.get("dir");
            String fileName = (String)payload.get("fileName");
            logger.info("download [{}] to [{}/{}]...", url, dir, fileName);

            HttpUtil.download(url, dir, fileName);

            logger.info("download [{}] to [{}/{}] complete.", url, dir, fileName);

            Response resp = new Response();
            return resp;
        });

        // execute
        rpcClient.registerService("execute", (ctx, payload) -> {
            String cmd = (String)payload.get("cmd");
            logger.info("execute [{}]...", cmd);

            String output = CmdUtil.execute(cmd);

            logger.info("execute [{}] complete -> \n{}\n", cmd, output);

            Response resp = new Response();
            resp.setData(URLEncoder.encode(output, "UTF-8"));
            return resp;
        });
    }

    public void connect(boolean await){
        rpcClient.connect();
        if(await){
            rpcClient.awaitConnected();
        }
    }
}
