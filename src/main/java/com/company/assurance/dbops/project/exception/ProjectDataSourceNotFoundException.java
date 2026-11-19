package com.company.assurance.dbops.project.exception;

public class ProjectDataSourceNotFoundException extends RuntimeException {

    public ProjectDataSourceNotFoundException(String projectDataSourceId) {
        super("Project data source not found: " + projectDataSourceId);
    }
}
