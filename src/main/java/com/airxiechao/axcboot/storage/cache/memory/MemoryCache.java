package com.airxiechao.axcboot.storage.cache.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryCache<K,V> {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCache.class);

    /**
     * 缓存仓库名称
     */
    private String cacheName;

    public MemoryCache(String cacheName){
        this.cacheName = cacheName;
    }

    private Map<K, V> dataMap = new ConcurrentHashMap<>();

    public void put(K key, V value){
        dataMap.put(key, value);
    }

    public V get(K key){
        return dataMap.get(key);
    }


}
