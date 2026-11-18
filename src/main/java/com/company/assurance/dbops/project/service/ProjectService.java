package com.company.assurance.dbops.project.service;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import com.company.assurance.dbops.project.repository.ProjectRepository;
import com.company.assurance.dbops.project.request.CreateProjectRequest;
import com.company.assurance.dbops.project.request.MapDataSourceRequest;
import com.company.assurance.dbops.project.request.MapEnvironmentRequest;
import com.company.assurance.dbops.project.response.ProjectDataSourceResponse;
import com.company.assurance.dbops.project.response.ProjectDetailResponse;
import com.company.assurance.dbops.project.response.ProjectEnvironmentResponse;
import com.company.assurance.dbops.project.response.ProjectSummaryResponse;
import com.company.assurance.dbops.project.entity.Project;
import com.company.assurance.dbops.project.entity.ProjectDataSourceMapping;
import com.company.assurance.dbops.project.entity.ProjectEnvironmentMapping;
import com.company.assurance.dbops.project.entity.ProjectStatus;
import com.company.assurance.dbops.project.exception.ProjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @Transactional
    public ProjectSummaryResponse createProject(CreateProjectRequest request) {
        String projectCode = normalizeCode(request.projectCode(), "projectCode");
        Instant now = clock.instant();

        Project project = new Project(
                UUID.randomUUID().toString(),
                projectCode,
                request.projectName().strip(),
                request.ownerName().strip(),
                ProjectStatus.ACTIVE,
                now,
                now
        );
        projectRepository.insert(project);
        return toSummary(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> listProjects() {
        return projectRepository.findAll().stream().map(ProjectService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(String projectId) {
        Project project = requireProject(projectId);
        List<ProjectEnvironmentMapping> environments = projectRepository.findEnvironments(project.id());
        List<ProjectDataSourceMapping> dataSources = projectRepository.findDataSources(project.id());
        return toDetail(project, environments, dataSources);
    }

    @Transactional
    public ProjectEnvironmentResponse mapEnvironment(String projectId, MapEnvironmentRequest request) {
        Project project = requireProject(projectId);
        Environment environment = request.environment();

        ProjectEnvironmentMapping mapping = new ProjectEnvironmentMapping(
                UUID.randomUUID().toString(),
                project.id(),
                environment,
                request.enabledOrDefault(),
                clock.instant()
        );
        projectRepository.insertEnvironment(mapping);
        projectRepository.touchProject(project.id(), clock.instant());
        return new ProjectEnvironmentResponse(mapping.environment(), mapping.enabled(), List.of());
    }

    @Transactional
    public ProjectDataSourceResponse mapDataSource(String projectId, MapDataSourceRequest request) {
        Project project = requireProject(projectId);
        Environment environment = request.environment();
        String dataSourceCode = normalizeCode(request.dataSourceCode(), "dataSourceCode");

        // Normalize through DataSourceKey so API codes match pool registry rules.
        DataSourceKey key = new DataSourceKey(project.projectCode(), environment, dataSourceCode);
        validateJdbcUrl(request.engine(), request.jdbcUrl());

        ProjectEnvironmentMapping environmentMapping = projectRepository
                .findEnvironment(project.id(), environment)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Environment is not mapped to this project: " + environment.name()
                ));

        Instant now = clock.instant();
        ProjectDataSourceMapping mapping = new ProjectDataSourceMapping(
                UUID.randomUUID().toString(),
                project.id(),
                environmentMapping.id(),
                environment,
                key.dataSourceCode(),
                request.engine(),
                blankToNull(request.schemaName()),
                request.jdbcUrl().strip(),
                request.username().strip(),
                request.secretRef().strip(),
                request.enabledOrDefault(),
                now,
                now
        );
        projectRepository.insertDataSource(mapping);
        projectRepository.touchProject(project.id(), now);
        return toDataSourceResponse(mapping);
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private static ProjectSummaryResponse toSummary(Project project) {
        return new ProjectSummaryResponse(
                project.id(),
                project.projectCode(),
                project.projectName(),
                project.ownerName(),
                project.status(),
                project.createdAt(),
                project.updatedAt()
        );
    }

    private static ProjectDetailResponse toDetail(Project project, List<ProjectEnvironmentMapping> environments, List<ProjectDataSourceMapping> dataSources) {
        Map<Environment, List<ProjectDataSourceResponse>> byEnvironment = new LinkedHashMap<>();
        for (ProjectEnvironmentMapping environment : environments) {
            byEnvironment.put(environment.environment(), new ArrayList<>());
        }
        for (ProjectDataSourceMapping dataSource : dataSources) {
            byEnvironment
                    .computeIfAbsent(dataSource.environment(), ignored -> new ArrayList<>())
                    .add(toDataSourceResponse(dataSource));
        }

        List<ProjectEnvironmentResponse> environmentResponses = environments.stream()
                .map(environment -> new ProjectEnvironmentResponse(
                        environment.environment(),
                        environment.enabled(),
                        List.copyOf(byEnvironment.getOrDefault(environment.environment(), List.of()))
                ))
                .toList();

        return new ProjectDetailResponse(
                project.id(),
                project.projectCode(),
                project.projectName(),
                project.ownerName(),
                project.status(),
                environmentResponses,
                project.createdAt(),
                project.updatedAt()
        );
    }

    private static ProjectDataSourceResponse toDataSourceResponse(ProjectDataSourceMapping mapping) {
        return new ProjectDataSourceResponse(
                mapping.id(),
                mapping.environment(),
                mapping.dataSourceCode(),
                mapping.engine(),
                mapping.schemaName(),
                mapping.username(),
                mapping.secretRef(),
                mapping.enabled()
        );
    }

    private static void validateJdbcUrl(DatabaseEngine engine, String jdbcUrl) {
        boolean compatible = switch (engine) {
            case ORACLE -> jdbcUrl.startsWith("jdbc:oracle:thin:@");
            case POSTGRESQL -> jdbcUrl.startsWith("jdbc:postgresql://");
        };
        if (!compatible) {
            throw new IllegalArgumentException("JDBC URL prefix must match engine " + engine.name());
        }
    }

    private static String normalizeCode(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
