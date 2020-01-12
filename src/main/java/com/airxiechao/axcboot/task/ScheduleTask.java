package com.airxiechao.axcboot.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ScheduleTask {

    private static ScheduledExecutorService executorService;
    static {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("schedule-task-%d")
                .setDaemon(true)
                .build();

        executorService = new ScheduledThreadPoolExecutor(10, threadFactory);
    }

    private static final int DAY_SECS = 60*60*24;

    /**
     * 启动定时任务
     * @param hour
     * @param minute
     * @param second
     * @param periodSecond
     * @param runNow
     * @param runnable
     */
    public static void schedule(int hour, int minute, int second, int periodSecond, boolean runNow, Runnable runnable){
        long initDelaySec = runNow ? 0 : calInitDelaySec(hour, minute, second);

        executorService.scheduleAtFixedRate(runnable, initDelaySec, periodSecond, TimeUnit.SECONDS);
    }

    /**
     * 启动周期定时任务
     * @param num
     * @param timeUnit
     * @param runnable
     */
    public static void shceduleEvery(int num, TimeUnit timeUnit, Runnable runnable){
        executorService.scheduleAtFixedRate(runnable, 0, num, timeUnit);
    }

    /**
     * 启动每日定时任务
     * @param hour
     * @param minute
     * @param second
     * @param runNow
     * @param runnable
     */
    public static void scheduleEveryDay(int hour, int minute, int second, boolean runNow, Runnable runnable){
        long initDelaySec = runNow ? 0 : calInitDelaySec(hour, minute, second);

        executorService.scheduleAtFixedRate(runnable, initDelaySec, DAY_SECS, TimeUnit.SECONDS);
    }

    /**
     * 计算初始延迟时间
     * @param hour
     * @param minute
     * @param second
     * @return
     */
    private static long calInitDelaySec(int hour, int minute, int second){
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, 0);
        if(!now.before(cal.getTime())){
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long delay = (cal.getTime().getTime() - now.getTime()) / 1000;

        return delay;
    }
}
