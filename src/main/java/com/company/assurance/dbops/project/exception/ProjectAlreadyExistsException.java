package com.company.assurance.dbops.project.exception;

public class ProjectAlreadyExistsException extends RuntimeException {
    public ProjectAlreadyExistsException(String projectCode) {
        super("Project already exists: " + projectCode);
    }
}
