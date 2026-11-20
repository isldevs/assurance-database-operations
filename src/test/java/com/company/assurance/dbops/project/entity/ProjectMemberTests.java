package com.company.assurance.dbops.project.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.assurance.dbops.project.authorization.ProjectPermission;
import com.company.assurance.dbops.project.authorization.ProjectRole;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectMemberTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void enabledMemberCanExerciseGrantedPermission() {

        ProjectMember member = member(ProjectRole.DBA, true);

        assertTrue(member.hasPermission(ProjectPermission.CONNECTION_TEST));
    }

    @Test
    void enabledMemberCannotExerciseUnauthorizedPermission() {

        ProjectMember member = member(ProjectRole.VIEWER, true);

        assertFalse(member.hasPermission(ProjectPermission.CONNECTION_TEST));
    }

    @ParameterizedTest
    @EnumSource(ProjectPermission.class)
    void disabledAdministratorCannotExerciseAnyPermission(ProjectPermission permission) {

        ProjectMember member = member(ProjectRole.PROJECT_ADMIN, false);

        assertFalse(member.hasPermission(permission));
    }

    @Test
    void rejectsNullPermission() {

        ProjectMember member = member(ProjectRole.PROJECT_ADMIN, true);

        assertFalse(member.hasPermission(null));
    }

    @Test
    void rejectsNullRole() {

        assertThrows(NullPointerException.class, () -> member(null, true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsInvalidUserId(String userId) {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectMember(
                                "member-1",
                                "project-1",
                                userId,
                                ProjectRole.VIEWER,
                                true,
                                "admin-1",
                                NOW));
    }

    @Test
    void rejectsOversizedUserId() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProjectMember(
                                "member-1",
                                "project-1",
                                "a".repeat(129),
                                ProjectRole.VIEWER,
                                true,
                                "admin-1",
                                NOW));
    }

    private static ProjectMember member(ProjectRole role, boolean enabled) {

        return new ProjectMember("member-1", "project-1", "user-1", role, enabled, "admin-1", NOW);
    }
}
