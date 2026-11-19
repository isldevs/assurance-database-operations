package com.company.assurance.dbops.datasource.api.exception;

public class DataSourceAlreadyExistsException extends RuntimeException {

    public DataSourceAlreadyExistsException(String dataSourceCode) {
        super("Data source already exists: " + dataSourceCode);
    }
}
