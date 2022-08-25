package com.imooc.api.config;

import org.redisson.Redisson;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * @author 高昂
 */
public class RedissonManager {
    private static Config config = new Config();
    private static RedissonClient redisson = null;
    private static final String RAtomicName = "genId_";

    public static void init() {
        try {
            config.useClusterServers()
                    .setScanInterval(200000)
                    .setMasterConnectionPoolSize(10000)
                    .setSlaveConnectionPoolSize(10000)
                    .setIdleConnectionTimeout(10000)
                    .setConnectTimeout(30000)
                    .setTimeout(3000)
                    .setRetryInterval(3000)
                    .setPassword("123456")
                    .addNodeAddress("redis://192.168.0.1:7000",
                            "redis://192.168.0.1:7001",
                            "redis://192.168.0.2:7000",
                            "redis://192.168.0.2:7001",
                            "redis://192.168.0.3:7000",
                            "redis://192.168.0.3:7001");
            redisson = Redisson.create(config);

            RAtomicLong atomicLong = redisson.getAtomicLong(RAtomicName);
            atomicLong.set(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static RedissonClient getRedisson() {
        if (redisson == null) {
            RedissonManager.init(); //初始化
        }
        return redisson;
    }
}
