package com.airxiechao.axcboot.storage.cache.db;

import com.airxiechao.axcboot.storage.db.DbManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DbCache<T> {

    private static final Logger logger = LoggerFactory.getLogger(DbCache.class);

    private Class<T> cls;

    public DbCache(Class<T> cls){
        this.cls = cls;
    }

    private Map<Long, T> dataMap = new ConcurrentHashMap<>();

    private void put(long id, T object){
        dataMap.put(id, object);
    }

    public T get(long id){
        T object = dataMap.get(id);
        if(null == object){
            object = DbManager.getInstance().getById(id, cls);
            if( null != object){
                put(id, object);
            }
        }

        return object;
    }
}
