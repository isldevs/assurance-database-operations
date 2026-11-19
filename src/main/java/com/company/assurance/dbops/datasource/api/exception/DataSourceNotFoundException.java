package com.company.assurance.dbops.datasource.api.exception;

public class DataSourceNotFoundException extends RuntimeException {

    public DataSourceNotFoundException(String dataSourceId) {
        super("Data source not found: " + dataSourceId);
    }
}
