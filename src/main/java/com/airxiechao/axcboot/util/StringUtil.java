package com.airxiechao.axcboot.util;

public class StringUtil {

    public static boolean isBlank(String str){
        if(null == str || str.isBlank()){
            return true;
        }

        return false;
    }

    public static boolean isEmpty(String str){
        if(null == str || str.isEmpty()){
            return true;
        }

        return false;
    }
}
