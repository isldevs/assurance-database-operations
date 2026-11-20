package com.company.assurance.dbops.datasource.registry;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import java.util.Objects;
import javax.sql.DataSource;
import org.jspecify.annotations.NonNull;

public record RegisteredDataSource(
        DataSourceKey key, DatabaseEngine engine, DataSource dataSource) {
    public RegisteredDataSource {
        Objects.requireNonNull(key, "Key is required");
        Objects.requireNonNull(engine, "Engine is required");
        Objects.requireNonNull(dataSource, "DataSource is required");
    }

    @Override
    @NonNull
    public String toString() {
        // Do not include the datasource object or connection details.
        return "RegisteredDataSource[key=" + key + ", engine=" + engine + "]";
    }
}
