package com.company.assurance.dbops.datasource.registry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.PoolProperties;
import com.company.assurance.dbops.datasource.connection.HikariPoolFactory;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DataSourceRegistryTests {

    @Test
    void resolvesTheExactEnvironmentAndRejectsUnknownTargets() {

        HikariPoolFactory factory = mock(HikariPoolFactory.class);
        HikariDataSource uatPool = mock(HikariDataSource.class);
        HikariDataSource stagePool = mock(HikariDataSource.class);

        var uat = registration(Environment.UAT, true);
        var stage = registration(Environment.STAGE, true);

        when(factory.create(uat)).thenReturn(uatPool);
        when(factory.create(stage)).thenReturn(stagePool);

        try (var registry = new DataSourceRegistry(properties(uat, stage), factory)) {
            assertSame(uatPool, registry.resolve(uat.key()).dataSource());
            assertSame(stagePool, registry.resolve(stage.key()).dataSource());
            assertThrows(
                    UnknownDataSourceException.class,
                    () ->
                            registry.resolve(
                                    new DataSourceKey("DEMO", Environment.STAGE2, "CORE_DB")));

            // Resolving repeatedly reuses the same pool.
            registry.resolve(uat.key());
            verify(factory, times(1)).create(uat);
        }

        verify(uatPool).close();
        verify(stagePool).close();
    }

    @Test
    void closesEarlierPoolsWhenLaterInitializationFails() {
        HikariPoolFactory factory = mock(HikariPoolFactory.class);
        HikariDataSource firstPool = mock(HikariDataSource.class);

        var uat = registration(Environment.UAT, true);
        var stage = registration(Environment.STAGE, true);

        when(factory.create(uat)).thenReturn(firstPool);
        when(factory.create(stage)).thenThrow(new IllegalStateException("Test failure"));

        assertThrows(
                IllegalStateException.class,
                () -> new DataSourceRegistry(properties(uat, stage), factory));

        verify(firstPool).close();
    }

    @Test
    void skipsDisabledRegistrations() {
        HikariPoolFactory factory = mock(HikariPoolFactory.class);
        try (var registry =
                new DataSourceRegistry(properties(registration(Environment.UAT, false)), factory)) {
            assertTrue(registry.keys().isEmpty());
            verifyNoInteractions(factory);
        }
    }

    @Test
    void shutdownIsIdempotentAndPreventsFurtherResolution() {
        HikariPoolFactory factory = mock(HikariPoolFactory.class);
        HikariDataSource pool = mock(HikariDataSource.class);
        var definition = registration(Environment.UAT, true);

        when(factory.create(definition)).thenReturn(pool);

        var registry = new DataSourceRegistry(properties(definition), factory);

        registry.close();
        registry.close();

        verify(pool, times(1)).close();

        assertThrows(IllegalStateException.class, () -> registry.resolve(definition.key()));
    }

    private static DatabaseOperationsProperties properties(
            DataSourceRegistrationProperties... registrations) {
        return new DatabaseOperationsProperties(24, List.of(registrations));
    }

    private static DataSourceRegistrationProperties registration(
            Environment environment, boolean enabled) {
        return new DataSourceRegistrationProperties(
                "DEMO",
                environment,
                "CORE_DB",
                DatabaseEngine.POSTGRESQL,
                "jdbc:postgresql://127.0.0.1:1/unused",
                "test",
                "test-fixture-not-a-real-secret",
                enabled,
                new PoolProperties(1, 0, 3000, 1000, 60000, 1800000, 0));
    }
}
