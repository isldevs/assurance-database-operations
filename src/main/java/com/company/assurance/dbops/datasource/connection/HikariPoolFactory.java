package com.company.assurance.dbops.datasource.connection;

import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.PoolProperties;
import com.company.assurance.dbops.datasource.validation.TargetApprovalValidator;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public final class HikariPoolFactory {

    private final TargetApprovalValidator targetApprovalValidator;
    private final MeterRegistry meterRegistry;

    public HikariPoolFactory(
            TargetApprovalValidator targetApprovalValidator, MeterRegistry meterRegistry) {
        this.targetApprovalValidator = targetApprovalValidator;
        this.meterRegistry = meterRegistry;
    }

    public HikariDataSource create(DataSourceRegistrationProperties registration) {
        // Always approve the target before creating its pool.
        targetApprovalValidator.assertApproved(registration);

        // The no-argument constructor defers pool startup until getConnection().
        HikariDataSource dataSource = new HikariDataSource();
        try {
            PoolProperties pool = registration.pool();

            dataSource.setPoolName(poolName(registration));
            dataSource.setJdbcUrl(registration.jdbcUrl());
            dataSource.setUsername(registration.username());
            dataSource.setPassword(registration.password());

            dataSource.setMaximumPoolSize(pool.maximumPoolSize());
            dataSource.setMinimumIdle(pool.minimumIdle());
            dataSource.setConnectionTimeout(pool.connectionTimeoutMs());
            dataSource.setValidationTimeout(pool.validationTimeoutMs());
            dataSource.setIdleTimeout(pool.idleTimeoutMs());
            dataSource.setMaxLifetime(pool.maximumLifetimeMs());
            dataSource.setLeakDetectionThreshold(pool.leakDetectionThresholdMs());

            // Do not fail the application because a target is currently offline.
            dataSource.setInitializationFailTimeout(-1);

            // Advisory JDBC read-only mode; database account grants enforce access.
            dataSource.setReadOnly(true);
            dataSource.setAutoCommit(true);

            // Register pool metrics when the lazy pool starts.
            dataSource.setMetricRegistry(meterRegistry);

            configureDriverTimeouts(dataSource, registration);

            // Validate Hikari settings without opening database connections.
            dataSource.validate();

            return dataSource;
        } catch (RuntimeException failure) {
            dataSource.close();

            // Do not propagate a driver/configuration message containing a URL.
            throw new IllegalStateException("Failed to configure a registered database pool");
        }
    }

    private static void configureDriverTimeouts(
            HikariDataSource dataSource, DataSourceRegistrationProperties registration) {
        switch (registration.engine()) {
            case ORACLE -> {
                dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
                // Oracle driver timeout values are milliseconds.
                dataSource.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "3000"); // 3s
                dataSource.addDataSourceProperty("oracle.jdbc.ReadTimeout", "5000"); // 5s
            }

            case POSTGRESQL -> {
                dataSource.setDriverClassName("org.postgresql.Driver");
                // PostgreSQL driver timeout values are seconds.
                dataSource.addDataSourceProperty("connectTimeout", "3");
                dataSource.addDataSourceProperty("socketTimeout", "5");
                dataSource.addDataSourceProperty("tcpKeepAlive", "true");
            }
        }
    }

    private static String poolName(DataSourceRegistrationProperties registration) {
        var key = registration.key();
        return "dbops."
                + key.projectCode()
                + "."
                + key.environment().name()
                + "."
                + key.dataSourceCode();
    }
}
