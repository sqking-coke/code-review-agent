package com.codereview.config;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.plugins.*;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.springframework.context.annotation.*;

/**
 * MyBatis-Plus 配置类
 *
 * <p>注册分页插件，支持 {@code Page<T>} 物理分页查询。
 * 数据库类型为 MySQL，自动适配 LIMIT 语法。</p>
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链
     *
     * <p>目前仅注册分页拦截器，后续可扩展乐观锁、多租户等插件。</p>
     *
     * @return 配置好的拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
