package com.company.assurance.dbops.datasource.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 3 enabled registrations × maximum pool size 3 = 9 connections 3 Kubernetes replicas × 9 = up to
 * 27 connections
 *
 * @param maximumTotalConnections
 * @param registrations
 */
@ConfigurationProperties(prefix = "db-operations")
public record DatabaseOperationsProperties(
        @DefaultValue("24") @Min(1) @Max(100) int maximumTotalConnections,
        @Valid @NotEmpty List<DataSourceRegistrationProperties> registrations) {

    public DatabaseOperationsProperties {
        registrations = registrations == null ? List.of() : List.copyOf(registrations);
    }

    @AssertTrue(message = "Duplicate project/environment/datasource registration")
    public boolean isRegistrationKeysUnique() {
        return registrations.stream()
                .collect(
                        Collectors.groupingBy(
                                registration ->
                                        List.of(
                                                normalize(registration.projectCode()),
                                                registration.environment() == null
                                                        ? ""
                                                        : registration.environment().name(),
                                                normalize(registration.dataSourceCode())),
                                Collectors.counting()))
                .values()
                .stream()
                .noneMatch(count -> count > 1);
    }

    @AssertTrue(message = "Enabled pools exceed maximum-total-connections")
    public boolean isConnectionBudgetValid() {
        long totalConnections =
                registrations.stream()
                        .filter(DataSourceRegistrationProperties::enabled)
                        .filter(registration -> registration.pool() != null)
                        .mapToLong(registration -> registration.pool().maximumPoolSize())
                        .sum();

        return totalConnections <= maximumTotalConnections;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
