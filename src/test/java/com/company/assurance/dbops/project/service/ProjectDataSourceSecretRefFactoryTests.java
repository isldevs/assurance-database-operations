package com.company.assurance.dbops.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.assurance.dbops.datasource.ProjectDataSourceSecretRefFactory;
import org.junit.jupiter.api.Test;

class ProjectDataSourceSecretRefFactoryTests {

    private static final String TEMPLATE =
            "projects/{PROJECT_CODE}/environments/{ENVIRONMENT}/data-sources/{DATA_SOURCE}";

    @Test
    void createsNormalizedSecretRefForProjectDatasourceMappingOnly() {
        ProjectDataSourceSecretRefFactory factory = new ProjectDataSourceSecretRefFactory(TEMPLATE);

        assertEquals(
                "projects/WING_CORE/environments/UAT/data-sources/WING_CORE",
                factory.forMapping("wing_core", "uat", "CORE_DB"));
        assertEquals(
                "projects/ORACLE/environments/UAT/data-sources/WING_ORACLE",
                factory.forMapping("oracle", "uat", "ORACLE_DB"));
    }

    @Test
    void rejectsUnsafePathSegmentsAndInvalidTemplates() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectDataSourceSecretRefFactory(
                                "projects/{PROJECT_CODE}/../{ENVIRONMENT}/{DATA_SOURCE}"));
        ProjectDataSourceSecretRefFactory factory = new ProjectDataSourceSecretRefFactory(TEMPLATE);
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.forMapping("WING/CORE", "UAT", "CORE_DB"));
    }
}
