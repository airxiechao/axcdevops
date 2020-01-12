package com.airxiechao.axcboot.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimeUtil {

    public static String toTimeStr(Date date){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(date);
    }

    public static String toTimeStr2(Date date){
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(date);
    }

    public static String toDateStr(Date date){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.format(date);
    }

    public static Date toTime(String time) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.parse(time);
    }

    public static Date toDate(String date) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.parse(date);
    }

    public static Date toNowDate() throws ParseException {
        return toDate(toDateStr(new Date()));
    }

    public static Date toNowTime() throws ParseException {
        return toTime(toTimeStr(new Date()));
    }

    public static Date clearTime(Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTime();
    }

    public static Date tomorrow(Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_YEAR, 1);

        return cal.getTime();
    }

    public static Date yesterday(Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_YEAR, -1);

        return cal.getTime();
    }

    /**
     * 计算时间差的文本
     * @param from
     * @param to
     * @return
     */
    public static String timeDiffText(Date from, Date to){
        long diff = to.getTime() - from.getTime();
        long days = diff / (1000 * 60 * 60 * 24);
        long hours = diff / (1000 * 60 * 60) % 24;
        long minutes = diff / (1000 * 60) % 60;
        if(diff % 1000 > 0){
            minutes += 1;
        }

        StringBuilder sb = new StringBuilder();
        if(days > 0){
            sb.append(days+"天");
        }
        if(days > 0 || hours > 0){
            sb.append(hours+"小时");
        }
        if(days > 0 || hours > 0 || minutes > 0){
            sb.append(minutes+"分钟");
        }

        return sb.toString();
    }
}
