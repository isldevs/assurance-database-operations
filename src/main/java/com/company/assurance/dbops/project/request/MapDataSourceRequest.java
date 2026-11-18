package com.company.assurance.dbops.project.request;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MapDataSourceRequest(
        @NotNull
        Environment environment,

        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}")
        String dataSourceCode,

        @NotNull
        DatabaseEngine engine,

        @Size(max = 128)
        String schemaName,

        @NotBlank
        @Size(max = 512)
        String jdbcUrl,

        @NotBlank
        @Size(max = 128)
        String username,

        @NotBlank
        @Size(max = 128)
        String secretRef,

        Boolean enabled) {
    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
