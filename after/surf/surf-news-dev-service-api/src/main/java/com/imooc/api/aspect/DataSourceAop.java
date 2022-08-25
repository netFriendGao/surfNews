package com.imooc.api.aspect;

import com.imooc.utils.DynamicDbUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author 高昂
 */
@Component
@Order(1)
@Aspect
@Slf4j
public class DataSourceAop {
    @Pointcut("@annotation(com.imooc.anno.ReadOnly)")
    public void readPointcut(){}

    /**
     * 配置前置通知，切换数据源为从数据库
     */
    @Before("readPointcut()")
    public void readAdvise(){
        log.info("切换数据源为从数据库");
        DynamicDbUtil.slave();
    }
}
