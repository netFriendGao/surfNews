package com.imooc.utils;

import com.imooc.api.config.RedissonManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonLock.class);
    public static final String REDIS_SUBMIT_COMMENT_DUP = "redis_submit_comment_dup";

    private static RedissonClient redissonClient = RedissonManager.getRedisson();

    public boolean lock(String lockName) {
        String key = REDIS_SUBMIT_COMMENT_DUP+lockName;
        RLock myLock = redissonClient.getLock(key);
        //lock提供带timeout参数，timeout结束强制解锁，防止死锁
        //myLock.lock(2, TimeUnit.SECONDS);
        // 1. 最常见的使用方法
        //lock.lock();
        // 2. 支持过期解锁功能,10秒以后自动解锁, 无需调用unlock方法手动解锁
        //lock.lock(10, TimeUnit.SECONDS);
        // 3. 尝试加锁，最多等待3秒，上锁以后10秒自动解锁
        boolean res=false;
        try {
            res = myLock.tryLock(0, 2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("res的结果是"+res);
        System.err.println("======lock======" + Thread.currentThread().getName());
        return res;
    }

    public void unLock(String lockName) {
        String key = REDIS_SUBMIT_COMMENT_DUP+lockName;;
        RLock myLock = redissonClient.getLock(key);
        myLock.unlock();
        System.err.println("======unlock======" + Thread.currentThread().getName());
    }
}
