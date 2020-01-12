package com.airxiechao.axcboot.communication.common;

public class Response {

    public static final String CODE_OK = "0";
    public static final String CODE_ERROR = "-1";
    public static final String CODE_AUTH_ERROR = "-2";

    private String code;
    private String message;
    private Object data;

    public Response(){
        this.code = CODE_OK;
    }

    public void success(){
        code = CODE_OK;
    }

    public void success(String message){
        this.code = CODE_OK;
        this.message = message;
    }

    public void error(){
        code = CODE_ERROR;
    }

    public void error(String message){
        this.code = CODE_ERROR;
        this.message = message;
    }

    public void authError(){
        code = CODE_AUTH_ERROR;
    }

    public void authError(String message){
        this.code = CODE_AUTH_ERROR;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean isSuccess(){
        return CODE_OK.equals(this.code);
    }
}
