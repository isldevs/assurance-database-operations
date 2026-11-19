package com.company.assurance.dbops.project.data.response;

import com.company.assurance.dbops.project.entity.ProjectStatus;
import com.company.assurance.dbops.project.entity.PromotionMode;
import java.time.Instant;
import java.util.List;

public record ProjectDetailResponse(
        String projectId,
        String projectCode,
        String projectName,
        String ownerName,
        ProjectStatus status,
        PromotionMode promotionMode,
        String createdBy,
        List<ProjectEnvironmentResponse> environments,
        Instant createdAt,
        Instant updatedAt) {}
