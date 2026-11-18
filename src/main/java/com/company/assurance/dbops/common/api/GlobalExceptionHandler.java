package com.company.assurance.dbops.common.api;

import com.company.assurance.dbops.project.exception.DuplicateProjectMappingException;
import com.company.assurance.dbops.project.exception.ProjectAlreadyExistsException;
import com.company.assurance.dbops.project.exception.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNotFound(
            ProjectNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.fail(
                                ApiError.of("PROJECT_NOT_FOUND", ex.getMessage()),
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

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                ApiError.of("BAD_REQUEST", ex.getMessage()),
                                correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
