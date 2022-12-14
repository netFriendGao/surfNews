package com.imooc.utils;

import com.imooc.enums.DBTypeEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 高昂
 */
@Slf4j
public class DynamicDbUtil {
    /**
     * 用来存储代表数据源的对象
     */
    private static final ThreadLocal<DBTypeEnum> CONTEXT_HAND = new ThreadLocal<>();

    /**
     * 切换当前线程要使用的数据源
     * @param dbTypeEnum
     */
    public static void set(DBTypeEnum dbTypeEnum){
        CONTEXT_HAND.set(dbTypeEnum);
        log.info("切换数据源:{}", dbTypeEnum);
    }

    /**
     * 切换到主数据库
     */
    public static void master(){
        set(DBTypeEnum.MASTER);
    }

    /**
     * 切换到从数据库
     */
    public static void slave(){
        set(DBTypeEnum.SLAVE);
    }

    /**
     * 移除当前线程使用的数据源
     */
    public static void remove(){
        CONTEXT_HAND.remove();
    }

    /**
     * 获取当前线程使用的枚举类
     * @return
     */
    public static DBTypeEnum get(){
        log.info(String.valueOf(CONTEXT_HAND.get()));
        return CONTEXT_HAND.get();
    }
}
