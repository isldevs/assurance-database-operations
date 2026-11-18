package com.company.assurance.dbops.project.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String projectId) {
        super("Project not found: " + projectId);
    }
}
