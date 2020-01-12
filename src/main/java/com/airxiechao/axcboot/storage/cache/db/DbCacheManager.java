package com.airxiechao.axcboot.storage.cache.db;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DbCacheManager {
    private static DbCacheManager ourInstance = new DbCacheManager();

    public static DbCacheManager getInstance() {
        return ourInstance;
    }

    private DbCacheManager() {
    }

    private Map<Class<?>, DbCache> caches = new ConcurrentHashMap<>();

    private <T> DbCache<T> createCache(Class<T> cls){
        DbCache<T> cache = new DbCache<>(cls);
        caches.put(cls, cache);
        return cache;
    }

    public <T> DbCache<T> getCache(Class<T> cls){
        DbCache<T> dbCache = caches.get(cls);
        if(null == dbCache){
            dbCache = createCache(cls);
        }

        return dbCache;
    }
}
