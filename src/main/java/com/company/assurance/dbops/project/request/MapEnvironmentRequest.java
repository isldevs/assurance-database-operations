package com.company.assurance.dbops.project.request;

import com.company.assurance.dbops.environment.Environment;
import jakarta.validation.constraints.NotNull;

public record MapEnvironmentRequest(@NotNull Environment environment, Boolean enabled) {
    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
