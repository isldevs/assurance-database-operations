package com.company.assurance.dbops.datasource.engine;

/** Enum representing different database engines. */
public enum DatabaseEngine {
    ORACLE("ORACLE", "Oracle"),
    POSTGRESQL("POSTGRESQL", "PostgreSQL");

    final String code;
    final String value;

    DatabaseEngine(String code, String value) {
        this.code = code;
        this.value = value;
    }
}
