package com.company.assurance.dbops.project.entity;

import com.company.assurance.dbops.project.authorization.ProjectPermission;
import com.company.assurance.dbops.project.authorization.ProjectRole;
import java.time.Instant;
import java.util.Objects;

public record ProjectMember(
        String id,
        String projectId,
        String userId,
        ProjectRole role,
        boolean enabled,
        String createdBy,
        Instant createdAt) {

    public ProjectMember {
        requireText(id, "id", 36);
        requireText(projectId, "projectId", 36);
        requireText(userId, "userId", 128);
        requireText(createdBy, "createdBy", 128);

        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean hasPermission(ProjectPermission permission) {
        return enabled && role.hasPermission(permission);
    }

    private static void requireText(String value, String field, int maxLength) {

        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}
