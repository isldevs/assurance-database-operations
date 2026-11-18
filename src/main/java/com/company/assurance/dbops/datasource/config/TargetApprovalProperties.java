package com.company.assurance.dbops.datasource.config;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "db-target-policy")
public record TargetApprovalProperties(@Valid List<ApprovedTarget> approvedTargets) {

    public TargetApprovalProperties {
        approvedTargets = approvedTargets == null ? List.of() : List.copyOf(approvedTargets);
    }

    public record ApprovedTarget(
            @NotBlank String projectCode,
            @NotNull Environment environment,
            @NotBlank String dataSourceCode,
            @NotNull DatabaseEngine engine,
            @NotBlank String jdbcUrl,
            boolean nonProductionConfirmed) {

        public DataSourceKey key() {
            return new DataSourceKey(projectCode, environment, dataSourceCode);
        }

        @Override
        public String toString() {
            // Keep internal connection details out of logs.
            return "ApprovedTarget[key="
                    + key()
                    + ", engine="
                    + engine
                    + ", nonProductionConfirmed="
                    + nonProductionConfirmed
                    + "]";
        }
    }
}
