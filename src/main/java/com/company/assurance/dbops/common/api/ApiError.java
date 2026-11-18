package com.company.assurance.dbops.common.api;

import java.util.List;

public record ApiError(String code, String message, List<ApiErrorDetail> details) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    public static ApiError of(String code, String message, List<ApiErrorDetail> details) {
        return new ApiError(code, message, details == null ? List.of() : List.copyOf(details));
    }
}
