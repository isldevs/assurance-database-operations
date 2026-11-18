package com.company.assurance.dbops.datasource.connection;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

public final class DataSourceErrorResolver {

    private DataSourceErrorResolver() {}

    public static String resolve(String engine, SQLException ex) {
        SQLException current = ex;

        while (current != null) {
            String reason =
                    switch (engine) {
                        case "ORACLE" -> resolveOracle(current.getErrorCode());
                        case "POSTGRESQL" -> resolvePostgres(current.getSQLState());
                        default -> null;
                    };

            if (reason != null) {
                return reason;
            }

            current = current.getNextException();
        }

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTimeoutException || cause instanceof SocketTimeoutException) {
                return "Database connection timeout";
            }
        }

        String sqlState = ex.getSQLState();

        if (sqlState != null && sqlState.startsWith("08")) {
            return "Database connection failure";
        }

        return "Database connectivity check failed";
    }

    private static String resolveOracle(int errorCode) {
        return switch (errorCode) {
            case 1017 -> "Invalid database credentials";
            case 1045 -> "Missing CREATE SESSION privilege";
            case 1034 -> "Oracle database unavailable";
            case 12154 -> "Database service identifier cannot be resolved";
            case 12170 -> "Database connection timeout";
            case 12505 -> "Database SID not registered";
            case 12514 -> "Database service not registered";
            case 12541 -> "Database listener unavailable";
            case 28000 -> "Database account locked";
            case 28001 -> "Database password expired";
            default -> null;
        };
    }

    private static String resolvePostgres(String sqlState) {
        if (sqlState == null) {
            return null;
        }

        return switch (sqlState) {
            case "28P01" -> "Invalid database credentials";
            case "28000" -> "Database authorization failed";
            case "3D000" -> "Database does not exist";
            case "53300" -> "Too many database connections";
            case "57P01" -> "Database administrator shutdown";
            case "57P03" -> "Database temporarily unavailable";
            case "42501" -> "Insufficient database privileges";
            default -> null;
        };
    }
}
