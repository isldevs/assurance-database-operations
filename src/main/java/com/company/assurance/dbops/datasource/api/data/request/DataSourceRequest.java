package com.company.assurance.dbops.datasource.api.data.request;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DataSourceRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String dataSourceCode,
        @NotBlank @Size(max = 128) String dataSourceName,
        @NotNull DatabaseEngine engine,
        @Size(max = 500) String description,
        Boolean enabled) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
