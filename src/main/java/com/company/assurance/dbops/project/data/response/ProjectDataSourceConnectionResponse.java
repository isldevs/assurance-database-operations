package com.company.assurance.dbops.project.data.response;

public record ProjectDataSourceConnectionResponse(
        String projectDataSourceId, boolean connected, long elapsedMs, String message) {}
