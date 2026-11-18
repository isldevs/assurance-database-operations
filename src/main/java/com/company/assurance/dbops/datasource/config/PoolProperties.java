package com.company.assurance.dbops.datasource.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record PoolProperties(

        @DefaultValue("3")
        @Min(1)
        @Max(10)
        int maximumPoolSize,

        @DefaultValue("0")
        @Min(0)
        int minimumIdle,

        @DefaultValue("3000")
        @Min(250)
        long connectionTimeoutMs,

        @DefaultValue("1000")
        @Min(250)
        long validationTimeoutMs,

        @DefaultValue("60000")
        @Min(10000)
        long idleTimeoutMs,

        @DefaultValue("1800000")
        @Min(30000)
        long maximumLifetimeMs,

        @DefaultValue("0")
        @Min(0)
        long leakDetectionThresholdMs) {

    @AssertTrue(message = "minimum-idle must not exceed maximum-pool-size")
    public boolean isIdleSizeValid() {
        return minimumIdle <= maximumPoolSize;
    }

    @AssertTrue(message = "validation-timeout-ms must be less than connection-timeout-ms")
    public boolean isValidationTimeoutValid() {
        return validationTimeoutMs < connectionTimeoutMs;
    }

    @AssertTrue(message = "leak-detection-threshold-ms must be zero or at least 2000")
    public boolean isLeakDetectionThresholdValid() {
        return leakDetectionThresholdMs == 0 || leakDetectionThresholdMs >= 2000;
    }
}
