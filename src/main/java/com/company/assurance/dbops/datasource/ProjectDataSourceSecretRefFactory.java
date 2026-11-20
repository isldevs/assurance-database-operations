package com.company.assurance.dbops.datasource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProjectDataSourceSecretRefFactory {

    private static final Pattern SECRET_REFERENCE =
            Pattern.compile("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*");
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{([A-Z_]+)}");
    private static final String PROJECT_CODE = "PROJECT_CODE";
    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final Set<String> REQUIRED_PLACEHOLDERS =
            Set.of(PROJECT_CODE, ENVIRONMENT, DATA_SOURCE);

    private final String pathTemplate;

    public ProjectDataSourceSecretRefFactory(@Value("${vault.secret-path}") String pathTemplate) {
        validateTemplate(pathTemplate);
        this.pathTemplate = pathTemplate.strip();
    }

    public String forMapping(String projectCode, String environment, String dataSourceCode) {
        Map<String, String> parameters =
                Map.of(
                        PROJECT_CODE, normalizeSegment(projectCode, PROJECT_CODE),
                        ENVIRONMENT, normalizeSegment(environment, ENVIRONMENT),
                        DATA_SOURCE, normalizeDataSourceCode(dataSourceCode));
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(pathTemplate);
        StringBuilder path = new StringBuilder(pathTemplate.length());
        while (matcher.find()) {
            String value = parameters.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("Unsupported placeholder in Vault secret path");
            }
            matcher.appendReplacement(path, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(path);
        return path.toString();
    }

    private static String normalizeSegment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!SAFE_SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + fieldName + " for Vault secret path");
        }
        return normalized;
    }

    private static String normalizeDataSourceCode(String dataSourceCode) {
        String normalized = normalizeSegment(dataSourceCode, DATA_SOURCE);
        if (normalized.endsWith("_DB")) {
            normalized = normalized.substring(0, normalized.length() - "_DB".length());
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid " + DATA_SOURCE + " for Vault secret path");
        }
        if (!normalized.startsWith("WING_")) {
            normalized = "WING_" + normalized;
        }
        if (!SAFE_SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + DATA_SOURCE + " for Vault secret path");
        }
        return normalized;
    }

    private static void validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Vault secret path template is required");
        }
        String strippedTemplate = template.strip();
        if (strippedTemplate.startsWith("/") || strippedTemplate.endsWith("/")) {
            throw new IllegalArgumentException(
                    "Vault secret path template must be a relative path");
        }
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(strippedTemplate);
        Set<String> placeholders = new HashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        String withoutPlaceholders = matcher.replaceAll("x");
        if (withoutPlaceholders.contains("{")
                || withoutPlaceholders.contains("}")
                || !SECRET_REFERENCE.matcher(withoutPlaceholders).matches()
                || Arrays.stream(withoutPlaceholders.split("/"))
                        .anyMatch(segment -> segment.equals(".") || segment.equals(".."))
                || !placeholders.containsAll(REQUIRED_PLACEHOLDERS)) {
            throw new IllegalArgumentException("Invalid Vault secret path template");
        }
        if (!REQUIRED_PLACEHOLDERS.containsAll(placeholders)) {
            throw new IllegalArgumentException("Unsupported placeholder in Vault secret path");
        }
    }
}
