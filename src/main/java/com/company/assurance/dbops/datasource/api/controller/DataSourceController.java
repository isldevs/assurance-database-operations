package com.company.assurance.dbops.datasource.api.controller;

import com.company.assurance.dbops.common.api.ApiResponse;
import com.company.assurance.dbops.common.api.CorrelationIdFilter;
import com.company.assurance.dbops.datasource.api.data.request.DataSourceRequest;
import com.company.assurance.dbops.datasource.api.data.response.DataSourceResponse;
import com.company.assurance.dbops.datasource.api.service.DataSourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ApiResponse<DataSourceResponse>> create(
            @Valid @RequestBody DataSourceRequest request, HttpServletRequest httpRequest) {
        DataSourceResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, correlationId(httpRequest)));
    }

    @GetMapping
    ApiResponse<List<DataSourceResponse>> list(HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.list(), correlationId(httpRequest));
    }

    @GetMapping("/{dataSourceId}")
    ApiResponse<DataSourceResponse> get(
            @PathVariable String dataSourceId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.get(dataSourceId), correlationId(httpRequest));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
