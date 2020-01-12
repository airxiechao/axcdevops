package com.airxiechao.axcboot.communication.rpc.common;

public class RpcMessage {

    private String requestId;
    private String type;
    private String payload;

    public RpcMessage(String requestId, String type, String payload){

        this.requestId = requestId;
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
