package com.company.assurance.dbops.datasource;

import com.company.assurance.dbops.environment.Environment;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * We need to normalize the project code, environment, and data source code to ensure consistency
 * and validation. {@code @example} -wing_core / UAT / wing_core -WING_CORE / UAT / WING_CORE
 *
 * @param projectCode
 * @param environment
 * @param dataSourceCode
 */
public record DataSourceKey(String projectCode, Environment environment, String dataSourceCode) {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public DataSourceKey {
        projectCode = normalize(projectCode, "projectCode");
        Objects.requireNonNull(environment, "environment is required");
        dataSourceCode = normalize(dataSourceCode, "dataSourceCode");
    }

    private static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String normalized = value.strip().toUpperCase(Locale.ROOT);

        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must start with a letter and contain only "
                            + "letters, digits, or underscores, up to 64 characters");
        }

        return normalized;
    }
}
