package com.company.assurance.dbops.datasource.api.entity;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import java.time.Instant;

public record DataSource(
        String id,
        String dataSourceCode,
        String dataSourceName,
        DatabaseEngine engine,
        String description,
        boolean enabled,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {}
