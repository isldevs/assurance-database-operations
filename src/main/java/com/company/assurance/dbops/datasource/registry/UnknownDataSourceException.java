package com.company.assurance.dbops.datasource.registry;

public final class UnknownDataSourceException extends RuntimeException {
    public UnknownDataSourceException() {
        super("Requested datasource registration was not found");
    }
}
