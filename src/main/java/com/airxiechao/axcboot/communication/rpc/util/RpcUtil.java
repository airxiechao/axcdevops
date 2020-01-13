package com.airxiechao.axcboot.communication.rpc.util;

import static com.airxiechao.axcboot.communication.rpc.common.RpcMessage.RESPONSE_SUFFIX;

public class RpcUtil {

    public static String buildResponseType(String requestType){
        return requestType + RESPONSE_SUFFIX;
    }

}
