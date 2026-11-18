package com.company.assurance.dbops.project.response;

import com.company.assurance.dbops.project.entity.ProjectStatus;
import java.time.Instant;

public record ProjectSummaryResponse(
        String projectId,
        String projectCode,
        String projectName,
        String ownerName,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {}
