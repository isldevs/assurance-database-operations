package com.company.assurance.dbops.datasource.config;

import com.company.assurance.dbops.datasource.connection.HikariPoolFactory;
import com.company.assurance.dbops.datasource.registry.DataSourceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataSourceRegistryConfiguration {

    @Bean(destroyMethod = "close")
    DataSourceRegistry dataSourceRegistry(
            DatabaseOperationsProperties properties, HikariPoolFactory poolFactory) {
        return new DataSourceRegistry(properties, poolFactory);
    }
}
