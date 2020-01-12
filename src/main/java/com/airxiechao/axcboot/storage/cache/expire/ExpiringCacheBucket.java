package com.airxiechao.axcboot.storage.cache.expire;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个单位时间内过期的数据桶
 * 时间表示过期时间
 */
public class ExpiringCacheBucket<T> {

    private Date expireDate;
    private Map<String, T> data;

    public ExpiringCacheBucket(Date expireDate){
        this.expireDate = expireDate;
        this.data = new ConcurrentHashMap<>();
    }

    public Date getExpireDate(){
        return this.expireDate;
    }

    public void put(String key, T value){
        this.data.put(key, value);
    }

    public T get(String key){
        return this.data.get(key);
    }

    public void remove(String key){
        this.data.remove(key);
    }

    public boolean containsKey(String key){
        return data.containsKey(key);
    }
}