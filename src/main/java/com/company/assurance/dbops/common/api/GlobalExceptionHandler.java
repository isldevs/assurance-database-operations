package com.company.assurance.dbops.common.api;

import com.company.assurance.dbops.datasource.api.exception.DataSourceAlreadyExistsException;
import com.company.assurance.dbops.datasource.api.exception.DataSourceNotFoundException;
import com.company.assurance.dbops.datasource.exception.VaultSecretException;
import com.company.assurance.dbops.project.exception.DuplicateProjectMappingException;
import com.company.assurance.dbops.project.exception.ProjectAlreadyExistsException;
import com.company.assurance.dbops.project.exception.ProjectDataSourceNotFoundException;
import com.company.assurance.dbops.project.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataSourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleDataSourceNotFound(
            DataSourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.fail(
                                ApiError.of("DATA_SOURCE_NOT_FOUND", ex.getMessage()),
                                correlationId(request)));
    }

    @ExceptionHandler(DataSourceAlreadyExistsException.class)
    ResponseEntity<ApiResponse<Void>> handleDataSourceConflict(
            DataSourceAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.fail(
                                ApiError.of("DATA_SOURCE_CONFLICT", ex.getMessage()),
                                correlationId(request)));
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(
            ProjectNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.fail(
                                ApiError.of("PROJECT_NOT_FOUND", ex.getMessage()),
                                correlationId(request)));
    }

    @ExceptionHandler(ProjectDataSourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleProjectDataSourceNotFound(
            ProjectDataSourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.fail(
                                ApiError.of("PROJECT_DATA_SOURCE_NOT_FOUND", ex.getMessage()),
                                correlationId(request)));
    }

    @ExceptionHandler({ProjectAlreadyExistsException.class, DuplicateProjectMappingException.class})
    ResponseEntity<ApiResponse<Void>> handleConflict(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.fail(
                                ApiError.of("PROJECT_CONFLICT", ex.getMessage()),
                                correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new ApiErrorDetail(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                ApiError.of(
                                        "VALIDATION_FAILED", "Request validation failed", details),
                                correlationId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details =
                ex.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new ApiErrorDetail(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                        .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                ApiError.of(
                                        "VALIDATION_FAILED", "Request validation failed", details),
                                correlationId(request)));
    }

    @ExceptionHandler(VaultSecretException.class)
    ResponseEntity<ApiResponse<Void>> handleVaultSecret(
            VaultSecretException ex, HttpServletRequest request) {

        HttpStatus status = switch (ex.getCode()) {
            case "VAULT_CONNECTION_FAILED" ->
                    HttpStatus.SERVICE_UNAVAILABLE;

            case "VAULT_SECRET_RETRIEVAL_FAILED",
                 "VAULT_INVALID_SECRET",
                 "VAULT_SECRET_FIELD_MISSING" ->
                    HttpStatus.BAD_GATEWAY;

            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.error(
                "Vault operation failed: code={}, correlationId={}",
                ex.getCode(),
                correlationId(request));

        List<ApiErrorDetail> details = List.of(
                new ApiErrorDetail("vault", ex.getCode()));

        return ResponseEntity.status(status)
                .body(
                        ApiResponse.fail(
                                ApiError.of(
                                        status.name(),
                                        status.getReasonPhrase(),
                                        details),
                                correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
