package com.company.assurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Primary {@code spring.datasource} is the metadata database (projects / envs / sources). Target
 * operational databases remain owned by {@code DataSourceRegistry}, not Boot's single DS.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
