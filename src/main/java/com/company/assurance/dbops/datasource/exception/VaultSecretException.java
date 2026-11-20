package com.company.assurance.dbops.datasource.exception;

public final class VaultSecretException extends RuntimeException {

    private final String code;
    private final Object[] arguments;

    public VaultSecretException(String code, Object... arguments) {
        this(code, null, arguments);
    }

    public VaultSecretException(String code, Throwable cause, Object... arguments) {
        super(code, cause);
        this.code = code;
        this.arguments = arguments.clone();
    }

    public String getCode() {
        return code;
    }

    public Object[] getArguments() {
        return arguments.clone();
    }
}
