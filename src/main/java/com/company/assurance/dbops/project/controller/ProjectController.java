package com.company.assurance.dbops.project.controller;

import com.company.assurance.dbops.common.api.ApiResponse;
import com.company.assurance.dbops.common.api.CorrelationIdFilter;
import com.company.assurance.dbops.project.request.CreateProjectRequest;
import com.company.assurance.dbops.project.request.MapDataSourceRequest;
import com.company.assurance.dbops.project.request.MapEnvironmentRequest;
import com.company.assurance.dbops.project.response.ProjectDataSourceResponse;
import com.company.assurance.dbops.project.response.ProjectDetailResponse;
import com.company.assurance.dbops.project.response.ProjectEnvironmentResponse;
import com.company.assurance.dbops.project.response.ProjectSummaryResponse;
import com.company.assurance.dbops.project.service.ProjectService;
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
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    ResponseEntity<ApiResponse<ProjectSummaryResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request, HttpServletRequest httpRequest) {
        ProjectSummaryResponse created = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, correlationId(httpRequest)));
    }

    @GetMapping
    ApiResponse<List<ProjectSummaryResponse>> listProjects(HttpServletRequest httpRequest) {
        return ApiResponse.ok(projectService.listProjects(), correlationId(httpRequest));
    }

    @GetMapping("/{projectId}")
    ApiResponse<ProjectDetailResponse> getProject(
            @PathVariable String projectId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(projectService.getProject(projectId), correlationId(httpRequest));
    }

    @PostMapping("/{projectId}/environments")
    ResponseEntity<ApiResponse<ProjectEnvironmentResponse>> mapEnvironment(
            @PathVariable String projectId,
            @Valid @RequestBody MapEnvironmentRequest request,
            HttpServletRequest httpRequest) {
        ProjectEnvironmentResponse mapped = projectService.mapEnvironment(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mapped, correlationId(httpRequest)));
    }

    @GetMapping("/{projectId}/environments")
    ApiResponse<List<ProjectEnvironmentResponse>> listEnvironments(
            @PathVariable String projectId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                projectService.getProject(projectId).environments(), correlationId(httpRequest));
    }

    @PostMapping("/{projectId}/data-sources")
    ResponseEntity<ApiResponse<ProjectDataSourceResponse>> mapDataSource(
            @PathVariable String projectId,
            @Valid @RequestBody MapDataSourceRequest request,
            HttpServletRequest httpRequest) {
        ProjectDataSourceResponse mapped = projectService.mapDataSource(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mapped, correlationId(httpRequest)));
    }

    @GetMapping("/{projectId}/data-sources")
    ApiResponse<List<ProjectDataSourceResponse>> listDataSources(
            @PathVariable String projectId, HttpServletRequest httpRequest) {
        List<ProjectDataSourceResponse> dataSources =
                projectService.getProject(projectId).environments().stream()
                        .flatMap(environment -> environment.dataSources().stream())
                        .toList();
        return ApiResponse.ok(dataSources, correlationId(httpRequest));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
