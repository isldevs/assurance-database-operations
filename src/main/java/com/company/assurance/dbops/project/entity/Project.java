package com.company.assurance.dbops.project.entity;

import java.time.Instant;

/**
 * @implNote Using record class for immutable value object
 * @param id
 * @param projectCode
 * @param projectName
 * @param ownerName
 * @param status
 * @param createdAt
 * @param updatedAt
 */
public record Project(
        String id,
        String projectCode,
        String projectName,
        String ownerName,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {}
