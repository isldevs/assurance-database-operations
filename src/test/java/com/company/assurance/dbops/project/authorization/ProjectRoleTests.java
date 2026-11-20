package com.company.assurance.dbops.project.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectRoleTests {

    @ParameterizedTest
    @CsvSource({
        "PROJECT_ADMIN, PROJECT_READ, true",
        "PROJECT_ADMIN, ENVIRONMENT_MANAGE, true",
        "PROJECT_ADMIN, DATASOURCE_MANAGE, true",
        "PROJECT_ADMIN, CONNECTION_TEST, true",
        "PROJECT_ADMIN, MEMBER_MANAGE, true",
        "DBA, PROJECT_READ, true",
        "DBA, ENVIRONMENT_MANAGE, false",
        "DBA, DATASOURCE_MANAGE, true",
        "DBA, CONNECTION_TEST, true",
        "DBA, MEMBER_MANAGE, false",
        "OPERATOR, PROJECT_READ, true",
        "OPERATOR, ENVIRONMENT_MANAGE, false",
        "OPERATOR, DATASOURCE_MANAGE, false",
        "OPERATOR, CONNECTION_TEST, true",
        "OPERATOR, MEMBER_MANAGE, false",
        "VIEWER, PROJECT_READ, true",
        "VIEWER, ENVIRONMENT_MANAGE, false",
        "VIEWER, DATASOURCE_MANAGE, false",
        "VIEWER, CONNECTION_TEST, false",
        "VIEWER, MEMBER_MANAGE, false"
    })
    void enforcesPermissionMatrix(
            ProjectRole role, ProjectPermission permission, boolean expected) {

        assertEquals(expected, role.hasPermission(permission));
    }

    @ParameterizedTest
    @CsvSource(
            value = {"PROJECT_ADMIN, NULL", "DBA, NULL", "OPERATOR, NULL", "VIEWER, NULL"},
            nullValues = "NULL")
    void rejectsNullPermission(ProjectRole role, ProjectPermission permission) {

        assertFalse(role.hasPermission(permission));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void permissionsCannotBeModified() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> ProjectRole.VIEWER.permissions().add(ProjectPermission.MEMBER_MANAGE));
    }

    @Test
    void permissionMatrixCoversAllRolesAndPermissions() {

        assertEquals(4, ProjectRole.values().length);
        assertEquals(5, ProjectPermission.values().length);
    }
}
