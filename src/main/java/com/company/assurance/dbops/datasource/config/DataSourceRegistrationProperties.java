package com.company.assurance.dbops.datasource.config;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record DataSourceRegistrationProperties(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String projectCode,
        @NotNull Environment environment,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String dataSourceCode,
        @NotNull DatabaseEngine engine,
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password,
        @DefaultValue("true") boolean enabled,
        @Valid @NotNull @DefaultValue PoolProperties pool) {

    public DataSourceKey key() {
        return new DataSourceKey(projectCode, environment, dataSourceCode);
    }

    @AssertTrue(message = "JDBC URL prefix must match the configured engine")
    public boolean isJdbcUrlCompatible() {
        if (engine == null || jdbcUrl == null || jdbcUrl.isBlank()) {
            // The individual field constraints report these missing values.
            return true;
        }

        return switch (engine) {
            case ORACLE -> jdbcUrl.startsWith("jdbc:oracle:thin:@");
            case POSTGRESQL -> jdbcUrl.startsWith("jdbc:postgresql://");
        };
    }

    @Override
    public String toString() {
        // Never include JDBC URLs, usernames, or passwords.
        return "DataSourceRegistrationProperties[projectCode="
                + projectCode
                + ", environment="
                + environment
                + ", dataSourceCode="
                + dataSourceCode
                + ", engine="
                + engine
                + ", enabled="
                + enabled
                + "]";
    }
}
