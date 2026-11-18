package com.company.assurance.dbops.project.entity;

import com.company.assurance.dbops.environment.Environment;
import java.time.Instant;

public record ProjectEnvironmentMapping(
        String id, String projectId, Environment environment, boolean enabled, Instant createdAt) {}
