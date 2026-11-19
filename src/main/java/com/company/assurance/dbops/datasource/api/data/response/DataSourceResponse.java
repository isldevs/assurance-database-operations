package com.company.assurance.dbops.datasource.api.data.response;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;

public record DataSourceResponse(
        String dataSourceId,
        String dataSourceCode,
        String dataSourceName,
        DatabaseEngine engine,
        String description,
        boolean enabled,
        String createdBy) {}
