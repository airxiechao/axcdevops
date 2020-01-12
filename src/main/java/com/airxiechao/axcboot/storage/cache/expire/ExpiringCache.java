package com.airxiechao.axcboot.storage.cache.expire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 过期缓存
 * 数据放在1个单位的时间桶里，时间桶的时间表示过期时间
 */
public class ExpiringCache<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExpiringCache.class);

    public enum UNIT {
        SECOND,
        MINUTE,
        HOUR,
        DAY,
    }

    /**
     * 缓存仓库名称
     */
    private String cacheName;

    /**
     * 过期的单位时间数目
     */
    private int expirePeriod;

    /**
     * 时间单位
     */
    private UNIT unit;

    /**
     * 缓存存储
     */
    private List<ExpiringCacheBucket<T>> buckets;

    /**
     * 构造函数
     * @param name
     * @param expirePeriod
     * @param unit
     */
    public ExpiringCache(String name, int expirePeriod, UNIT unit){
        this.cacheName = name;
        this.expirePeriod = expirePeriod;
        this.unit = unit;
        this.buckets = new LinkedList<>();
    }

    /**
     * 【事务】先清理过期，再添加到缓存，如果已存在则更新
     * @param key
     * @param value
     */
    public void put(String key, T value){
        synchronized (this){
            _clearExpired();
            _put(key, value);
        }
    }

    /**
     * 【事务】先清理过期，检查没有再添加
     * @param key
     * @param value
     * @return
     */
    public boolean checkThenPut(String key, T value){
        synchronized (this){
            _clearExpired();

            if(_containsKey(key)){
                return false;
            }else{
                _put(key, value);
                return true;
            }
        }
    }

    /**
     * 【事务】先清理过期，再从缓存获取
     * @param key
     * @return
     */
    public T get(String key){
        synchronized (this){
            _clearExpired();
            return _get(key);
        }
    }

    /**
     * 【事务】先清理过期，再检查是否存在键
     */
    public boolean containsKey(String key){
        synchronized (this){
            _clearExpired();
            return _containsKey(key);
        }
    }

    /**
     * 【事务】先清理过期，再删除键
     */
    public void remove(String key){
        synchronized (this){
            _clearExpired();
            _remove(key);
        }
    }


    /**
     * 清理过期缓存
     */
    private void _clearExpired(){
        Iterator<ExpiringCacheBucket<T>> iter = buckets.iterator();
        while(iter.hasNext()){
            ExpiringCacheBucket bucket = iter.next();
            Date expireDate = bucket.getExpireDate();
            Date now = new Date();
            if(now.after(expireDate)){
                iter.remove();
            }else{
                break;
            }
        }
    }

    /**
     * 添加
     * @param key
     */
    private void _put(String key, T value){
        /**
         * first find if there is a bucket containing key
         * if exists, remove key from that bucket
         */
        ExpiringCacheBucket<T> bucket = _getBucketByKey(key);
        if(null != bucket){
            bucket.remove(key);
        }

        /**
         * find if there is a bucket which expire date is current date + period
         * if not exist, create bucket
         */
        Date expireDate = _getCurrentBucketExpiredDate();
        bucket = _getBucketByExpireDate(expireDate);
        if(null == bucket){
            // if no bucket
            bucket = new ExpiringCacheBucket<>(expireDate);
            buckets.add(bucket);
        }

        bucket.put(key, value);
    }


    /**
     * 获取
     * @param key
     */
    private T _get(String key){
        ExpiringCacheBucket<T> bucket = _getBucketByKey(key);
        if(null != bucket){
            return bucket.get(key);
        }

        return null;
    }

    /**
     * 删除
     * @param key
     */
    private void _remove(String key){
        ExpiringCacheBucket<T> bucket = _getBucketByKey(key);
        if(null != bucket){
            bucket.remove(key);
        }
    }

    /**
     * 是否存在键
     * @param key
     * @return
     */
    private boolean _containsKey(String key){
        Iterator<ExpiringCacheBucket<T>> iter = buckets.iterator();
        while(iter.hasNext()){
            ExpiringCacheBucket bucket = iter.next();
            if(bucket.containsKey(key)){
                return true;
            }
        }

        return false;
    }

    /**
     * 获取包含某个时间的数据桶
     * @param date
     * @return
     */
    private ExpiringCacheBucket<T> _getBucketByExpireDate(Date date){
        Iterator<ExpiringCacheBucket<T>> iter = buckets.iterator();
        while(iter.hasNext()){
            ExpiringCacheBucket bucket = iter.next();
            Date dateTo = bucket.getExpireDate();
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateTo);
            cal.add(_convertToCalendarUnit(unit), -1);
            Date dateFrom = cal.getTime();

            if(date.getTime() > dateFrom.getTime() && date.getTime() <= dateTo.getTime()){
                return bucket;
            }
        }

        return null;
    }

    /**
     * 获取包含某个key的数据桶
     * @param key
     * @return
     */
    private ExpiringCacheBucket _getBucketByKey(String key){
        Iterator<ExpiringCacheBucket<T>> iter = buckets.iterator();
        while(iter.hasNext()){
            ExpiringCacheBucket bucket = iter.next();

            if(bucket.containsKey(key)){
                return bucket;
            }
        }

        return null;
    }

    /**
     * 得到当前时间的bucket的过期时间
     * @return
     */
    private Date _getCurrentBucketExpiredDate(){
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);

        switch (unit){
            case DAY:
                cal.set(Calendar.HOUR_OF_DAY, 0);
            case HOUR:
                cal.set(Calendar.MINUTE, 0);
            case MINUTE:
                cal.set(Calendar.SECOND, 0);
            case SECOND:
                cal.set(Calendar.MILLISECOND, 0);
        }

        cal.add(_convertToCalendarUnit(unit), expirePeriod);
        Date expireDate = cal.getTime();

        return expireDate;
    }

    /**
     * 时间单位转换为Calendar单位
     * @param unit
     * @return
     */
    private int _convertToCalendarUnit(UNIT unit){
        switch (unit){
            case SECOND:
                return Calendar.SECOND;
            case MINUTE:
                return Calendar.MINUTE;
            case HOUR:
                return Calendar.HOUR;
            case DAY:
                return Calendar.DAY_OF_YEAR;
            default:
                return Calendar.SECOND;
        }
    }
}


