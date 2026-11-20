package com.company.assurance.dbops.project.authorization;

import java.util.Set;

public enum ProjectRole {
    PROJECT_ADMIN(
            Set.of(
                    ProjectPermission.PROJECT_READ,
                    ProjectPermission.ENVIRONMENT_MANAGE,
                    ProjectPermission.DATASOURCE_MANAGE,
                    ProjectPermission.CONNECTION_TEST,
                    ProjectPermission.MEMBER_MANAGE)),

    DBA(
            Set.of(
                    ProjectPermission.PROJECT_READ,
                    ProjectPermission.DATASOURCE_MANAGE,
                    ProjectPermission.CONNECTION_TEST)),

    OPERATOR(Set.of(ProjectPermission.PROJECT_READ, ProjectPermission.CONNECTION_TEST)),

    VIEWER(Set.of(ProjectPermission.PROJECT_READ));

    private final Set<ProjectPermission> permissions;

    ProjectRole(Set<ProjectPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(ProjectPermission permission) {
        return permission != null && permissions.contains(permission);
    }

    public Set<ProjectPermission> permissions() {
        return permissions;
    }
}
