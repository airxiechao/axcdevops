package com.airxiechao.axcboot.util;

import java.util.HashMap;
import java.util.Map;

public class MapBuilder {

    private Map<String, Object> map;

    public MapBuilder(){
        this.map = new HashMap<>();
    }

    public MapBuilder put(String key, Object value){
        this.map.put(key, value);
        return this;
    }

    public Map<String, Object> build(){
        return this.map;
    }

}
