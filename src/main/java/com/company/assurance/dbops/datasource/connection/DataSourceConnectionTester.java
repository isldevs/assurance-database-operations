package com.company.assurance.dbops.datasource.connection;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.datasource.validation.TargetApprovalValidator;
import java.util.Arrays;
import java.util.Properties;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Component
public class DataSourceConnectionTester {

    private final TargetApprovalValidator targetApprovalValidator;

    public DataSourceConnectionTester(TargetApprovalValidator targetApprovalValidator) {
        this.targetApprovalValidator = targetApprovalValidator;
    }

    public boolean test(
            DataSourceKey key, DatabaseEngine engine, String jdbcUrl, String username, char[] password) {
        try {
            targetApprovalValidator.assertApproved(key, engine, jdbcUrl);
            DriverManagerDataSource target = new DriverManagerDataSource();
            target.setDriverClassName(driverClassName(engine));
            target.setUrl(jdbcUrl);
            target.setUsername(username);

            Properties properties = new Properties();
            properties.setProperty("password", new String(password));
            configureDriverTimeouts(properties, engine);
            target.setConnectionProperties(properties);

            return Boolean.TRUE.equals(
                    new JdbcTemplate(target)
                            .execute(
                                    (ConnectionCallback<Boolean>)
                                            connection -> connection.isValid(3)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String driverClassName(DatabaseEngine engine) {
        return switch (engine) {
            case ORACLE -> "oracle.jdbc.OracleDriver";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
    }

    private static void configureDriverTimeouts(Properties properties, DatabaseEngine engine) {
        switch (engine) {
            case ORACLE -> {
                properties.setProperty("oracle.net.CONNECT_TIMEOUT", "3000");
                properties.setProperty("oracle.jdbc.ReadTimeout", "5000");
            }
            case POSTGRESQL -> {
                properties.setProperty("connectTimeout", "3");
                properties.setProperty("socketTimeout", "5");
                properties.setProperty("tcpKeepAlive", "true");
            }
        }
    }
}
