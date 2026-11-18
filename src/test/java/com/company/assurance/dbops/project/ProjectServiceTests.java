package com.company.assurance.dbops.project;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import com.company.assurance.dbops.project.request.CreateProjectRequest;
import com.company.assurance.dbops.project.request.MapDataSourceRequest;
import com.company.assurance.dbops.project.request.MapEnvironmentRequest;
import com.company.assurance.dbops.project.response.ProjectDetailResponse;
import com.company.assurance.dbops.project.entity.Project;
import com.company.assurance.dbops.project.entity.ProjectDataSourceMapping;
import com.company.assurance.dbops.project.entity.ProjectEnvironmentMapping;
import com.company.assurance.dbops.project.entity.ProjectStatus;
import com.company.assurance.dbops.project.repository.ProjectRepository;
import com.company.assurance.dbops.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock
    private ProjectRepository projectRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createProjectNormalizesCode() {
        var response = projectService.createProject(
                new CreateProjectRequest("wing_core", "Core Banking", "Core Team")
        );

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).insert(captor.capture());
        assertEquals("WING_CORE", captor.getValue().projectCode());
        assertEquals("WING_CORE", response.projectCode());
    }

    @Test
    void getProjectReturnsMultipleEnvironmentsAndDataSources() {
        Project project = new Project(
                "project-1",
                "WING_CORE",
                "Core Banking",
                "Core Team",
                ProjectStatus.ACTIVE,
                NOW,
                NOW
        );
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(projectRepository.findEnvironments("project-1")).thenReturn(List.of(
                new ProjectEnvironmentMapping("env-uat", "project-1", Environment.UAT, true, NOW),
                new ProjectEnvironmentMapping("env-stage", "project-1", Environment.STAGE, true, NOW)
        ));
        when(projectRepository.findDataSources("project-1")).thenReturn(List.of(
                dataSource("ds-1", "env-uat", Environment.UAT, "CORE_DB", DatabaseEngine.ORACLE),
                dataSource("ds-2", "env-uat", Environment.UAT, "PERSONA_DB", DatabaseEngine.ORACLE),
                dataSource("ds-3", "env-stage", Environment.STAGE, "CORE_DB", DatabaseEngine.ORACLE),
                dataSource("ds-4", "env-stage", Environment.STAGE, "REPORTING_DB", DatabaseEngine.POSTGRESQL)
        ));

        ProjectDetailResponse detail = projectService.getProject("project-1");

        assertEquals(2, detail.environments().size());
        assertEquals(2, detail.environments().get(0).dataSources().size());
        assertEquals(2, detail.environments().get(1).dataSources().size());
        assertTrue(detail.environments().get(0).dataSources().stream()
                .noneMatch(ds -> ds.secretRef() == null));
    }

    @Test
    void mapDataSourceRequiresEnvironmentMappingFirst() {
        Project project = new Project(
                "project-1",
                "WING_CORE",
                "Core Banking",
                "Core Team",
                ProjectStatus.ACTIVE,
                NOW,
                NOW
        );
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(projectRepository.findEnvironment("project-1", Environment.UAT)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> projectService.mapDataSource(
                "project-1",
                new MapDataSourceRequest(
                        Environment.UAT,
                        "CORE_DB",
                        DatabaseEngine.POSTGRESQL,
                        "public",
                        "jdbc:postgresql://127.0.0.1:5433/dbops_metadata",
                        "dbops_app",
                        "DEMO_POSTGRES_APP_PASSWORD",
                        true
                )
        ));
    }

    @Test
    void mapEnvironmentPersistsMapping() {
        Project project = new Project(
                "project-1",
                "WING_CORE",
                "Core Banking",
                "Core Team",
                ProjectStatus.ACTIVE,
                NOW,
                NOW
        );
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        projectService.mapEnvironment("project-1", new MapEnvironmentRequest(Environment.UAT, null));

        verify(projectRepository).insertEnvironment(any(ProjectEnvironmentMapping.class));
        verify(projectRepository).touchProject("project-1", NOW);
    }

    private static ProjectDataSourceMapping dataSource(
            String id,
            String envId,
            Environment environment,
            String code,
            DatabaseEngine engine
    ) {
        return new ProjectDataSourceMapping(
                id,
                "project-1",
                envId,
                environment,
                code,
                engine,
                "MWING",
                "jdbc:postgresql://127.0.0.1:5433/" + code.toLowerCase(),
                "dbops_app",
                "SECRET_" + code,
                true,
                NOW,
                NOW
        );
    }
}
