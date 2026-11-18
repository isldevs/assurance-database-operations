package com.company.assurance.dbops.project.response;

import com.company.assurance.dbops.project.entity.ProjectStatus;

import java.time.Instant;
import java.util.List;

public record ProjectDetailResponse(
        String projectId,
        String projectCode,
        String projectName,
        String ownerName,
        ProjectStatus status,
        List<ProjectEnvironmentResponse> environments,
        Instant createdAt,
        Instant updatedAt
) {
}
