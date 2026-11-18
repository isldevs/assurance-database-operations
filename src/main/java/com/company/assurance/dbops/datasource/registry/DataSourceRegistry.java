package com.company.assurance.dbops.datasource.registry;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.connection.HikariPoolFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DataSourceRegistry implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistry.class);

    private final Map<DataSourceKey, RegisteredDataSource> registrations;
    private final Map<DataSourceKey, HikariDataSource> ownedPools;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DataSourceRegistry(DatabaseOperationsProperties properties, HikariPoolFactory poolFactory) {
        Map<DataSourceKey, RegisteredDataSource> entries = new HashMap<>();
        Map<DataSourceKey, HikariDataSource> pools = new HashMap<>();
        try {
            for (var definition : properties.registrations()) {
                if (!definition.enabled()) {
                    continue;
                }
                DataSourceKey key = definition.key();
                // Defensive check even though startup configuration validates duplicates.
                if (entries.containsKey(key)) {
                    throw new IllegalStateException("Duplicate enabled datasource registration");
                }
                HikariDataSource pool = poolFactory.create(definition);
                // Track ownership immediately so later failures can clean it up.
                pools.put(key, pool);
                entries.put(key, new RegisteredDataSource(key, definition.engine(), pool));
            }
            ownedPools = Map.copyOf(pools);
            registrations = Map.copyOf(entries);
        } catch (RuntimeException failure) {
            closePools(pools);
            throw failure;
        }
    }

    public RegisteredDataSource resolve(DataSourceKey key) {
        if (closed.get()) {
            throw new IllegalStateException("Datasource registry is closed");
        }
        RegisteredDataSource registration = registrations.get(key);
        if (registration == null) {
            throw new UnknownDataSourceException();
        }
        return registration;
    }

    public List<DataSourceKey> keys() {
        return List.copyOf(registrations.keySet());
    }

    @Override
    public void close() {
        // Closing twice is safe.
        if (closed.compareAndSet(false, true)) {
            closePools(ownedPools);
        }
    }

    private static void closePools(Map<DataSourceKey, HikariDataSource> pools) {
        for (HikariDataSource pool : pools.values()) {
            try {
                pool.close();
            } catch (RuntimeException ignored) {
                // Continue closing remaining pools. Avoid logging driver details.
                log.warn("A registered database pool could not be closed");
            }
        }
    }
}
