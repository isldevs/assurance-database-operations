package com.company.assurance.dbops.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, String correlationId) {
    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId);
    }

    public static <T> ApiResponse<T> fail(ApiError error, String correlationId) {
        return new ApiResponse<>(false, null, error, correlationId);
    }
}
