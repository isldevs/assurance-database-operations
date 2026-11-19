package com.company.assurance.dbops.datasource;

import java.util.Arrays;

public record DatabaseCredentials(String username, char[] password) {

    public DatabaseCredentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Database credential username is required");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Database credential password is required");
        }
    }

    public void clearPassword() {
        Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
        return "DatabaseCredentials[username=REDACTED, password=REDACTED]";
    }
}
