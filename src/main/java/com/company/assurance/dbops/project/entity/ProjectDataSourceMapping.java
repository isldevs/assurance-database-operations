package com.company.assurance.dbops.project.entity;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import java.time.Instant;

public record ProjectDataSourceMapping(
        String id,
        String projectId,
        String projectEnvironmentId,
        Environment environment,
        String dataSourceId,
        String dataSourceCode,
        DatabaseEngine engine,
        String schemaName,
        String jdbcUrl,
        String secretRef,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {}
