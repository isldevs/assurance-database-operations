package com.company.assurance.dbops.datasource.validation;

public final class TargetNotApprovedException extends RuntimeException {

    public TargetNotApprovedException() {
        super("Database target is not approved");
    }
}
