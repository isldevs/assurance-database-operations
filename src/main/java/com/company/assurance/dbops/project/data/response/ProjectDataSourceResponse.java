package com.company.assurance.dbops.project.data.response;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;

/**
 * API responses never include JDBC URLs or passwords. Credentials are referenced by {@code
 * secretRef} for Vault / env resolution.
 */
public record ProjectDataSourceResponse(
        String projectDataSourceId,
        String dataSourceId,
        Environment environment,
        String dataSourceCode,
        DatabaseEngine engine,
        String schemaName,
        String secretRef,
        boolean enabled) {}
