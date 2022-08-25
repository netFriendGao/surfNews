package com.imooc.abstr;

import com.imooc.utils.DynamicDbUtil;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;


/**
 * @author 高昂
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    /**
     * 返回当前线程正在使用代表数据库的枚举对象
     *
     * @return
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDbUtil.get();
    }
}