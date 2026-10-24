package com.teacheros.config;

import com.teacheros.common.security.rls.TenantAwareDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class RlsDataSourceConfig {

    // static: BeanPostProcessors must be created early, before this config class
    // itself.
    @Bean
    @ConditionalOnProperty(name = "app.security.rls.enabled", havingValue = "true", matchIfMissing = true)
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }
}
