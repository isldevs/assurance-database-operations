package com.company.assurance.dbops.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.DataSourceSecretResolver;
import com.company.assurance.dbops.datasource.DatabaseCredentials;
import com.company.assurance.dbops.datasource.ProjectDataSourceSecretRefFactory;
import com.company.assurance.dbops.datasource.api.entity.DataSource;
import com.company.assurance.dbops.datasource.api.repository.DataSourceRepository;
import com.company.assurance.dbops.datasource.connection.DataSourceConnectionTester;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.datasource.validation.TargetApprovalValidator;
import com.company.assurance.dbops.datasource.validation.TargetNotApprovedException;
import com.company.assurance.dbops.environment.Environment;
import com.company.assurance.dbops.project.data.request.CreateProjectRequest;
import com.company.assurance.dbops.project.data.request.MapDataSourceRequest;
import com.company.assurance.dbops.project.data.request.MapEnvironmentRequest;
import com.company.assurance.dbops.project.data.response.ProjectDataSourceResponse;
import com.company.assurance.dbops.project.data.response.ProjectDetailResponse;
import com.company.assurance.dbops.project.entity.Project;
import com.company.assurance.dbops.project.entity.ProjectDataSourceMapping;
import com.company.assurance.dbops.project.entity.ProjectEnvironmentMapping;
import com.company.assurance.dbops.project.entity.ProjectStatus;
import com.company.assurance.dbops.project.entity.PromotionMode;
import com.company.assurance.dbops.project.repository.ProjectRepository;
import com.company.assurance.dbops.project.service.ProjectService;
import com.company.assurance.dbops.security.CustomSecurityContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String DATA_SOURCE_ID = "source-1";
    private static final String DATA_SOURCE_CODE = "CORE_DB";

    @Mock private ProjectRepository projectRepository;
    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private DataSourceSecretResolver secretResolver;
    @Mock private ProjectDataSourceSecretRefFactory secretRefFactory;
    @Mock private DataSourceConnectionTester connectionTester;
    @Mock private CustomSecurityContextHolder customSecurityContextHolder;
    @Mock private TargetApprovalValidator targetApprovalValidator;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        lenient().when(customSecurityContextHolder.currentUser()).thenReturn("test-user");

        projectService =
                new ProjectService(
                        projectRepository,
                        dataSourceRepository,
                        secretResolver,
                        secretRefFactory,
                        connectionTester,
                        targetApprovalValidator,
                        customSecurityContextHolder,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createProjectNormalizesCode() {
        var response =
                projectService.createProject(
                        new CreateProjectRequest("wing_core", "Core Banking", "Core Team"));

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).insert(captor.capture());
        assertEquals("WING_CORE", captor.getValue().projectCode());
        assertEquals("test-user", captor.getValue().createdBy());
        assertEquals(PromotionMode.SEQUENTIAL, captor.getValue().promotionMode());
        assertEquals("WING_CORE", response.projectCode());
        assertEquals("test-user", response.createdBy());
    }

    @Test
    void createProjectAcceptsIndependentPromotionMode() {
        var response =
                projectService.createProject(
                        new CreateProjectRequest(
                                "wing_core",
                                "Core Banking",
                                "Core Team",
                                PromotionMode.INDEPENDENT));

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).insert(captor.capture());
        assertEquals(PromotionMode.INDEPENDENT, captor.getValue().promotionMode());
        assertEquals(PromotionMode.INDEPENDENT, response.promotionMode());
    }

    @Test
    void getProjectReturnsMultipleEnvironmentsAndDataSources() {
        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(projectRepository.findEnvironments("project-1"))
                .thenReturn(
                        List.of(
                                new ProjectEnvironmentMapping(
                                        "env-uat", "project-1", Environment.UAT, true, NOW),
                                new ProjectEnvironmentMapping(
                                        "env-stage", "project-1", Environment.STAGE, true, NOW)));
        when(projectRepository.findDataSources("project-1"))
                .thenReturn(
                        List.of(
                                dataSource(
                                        "ds-1",
                                        "env-uat",
                                        Environment.UAT,
                                        "source-1",
                                        "CORE_DB",
                                        DatabaseEngine.ORACLE),
                                dataSource(
                                        "ds-2",
                                        "env-uat",
                                        Environment.UAT,
                                        "source-2",
                                        "PERSONA_DB",
                                        DatabaseEngine.ORACLE),
                                dataSource(
                                        "ds-3",
                                        "env-stage",
                                        Environment.STAGE,
                                        "source-1",
                                        "CORE_DB",
                                        DatabaseEngine.ORACLE),
                                dataSource(
                                        "ds-4",
                                        "env-stage",
                                        Environment.STAGE,
                                        "source-3",
                                        "REPORTING_DB",
                                        DatabaseEngine.POSTGRESQL)));

        ProjectDetailResponse detail = projectService.getProject("project-1");

        assertEquals(2, detail.environments().size());
        assertEquals(2, detail.environments().get(0).dataSources().size());
        assertEquals(2, detail.environments().get(1).dataSources().size());
        assertTrue(
                detail.environments().get(0).dataSources().stream()
                        .noneMatch(ds -> ds.secretRef() == null));
    }

    @Test
    void mapDataSourceRequiresEnvironmentMappingFirst() {
        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(dataSourceRepository.findByCode("CORE_DB"))
                .thenReturn(Optional.of(dataSourceDefinition()));
        when(projectRepository.findEnvironment("project-1", Environment.UAT))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        projectService.mapDataSource(
                                "project-1",
                                new MapDataSourceRequest(
                                        Environment.UAT,
                                        "CORE_DB",
                                        "public",
                                        "jdbc:postgresql://127.0.0.1:5433/dbops",
                                        true)));
    }

    @Test
    void mapEnvironmentPersistsMapping() {
        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        projectService.mapEnvironment(
                "project-1", new MapEnvironmentRequest(Environment.UAT, null));

        verify(projectRepository).insertEnvironment(any(ProjectEnvironmentMapping.class));
        verify(projectRepository).touchProject("project-1", NOW);
    }

    @Test
    void mapDataSourcePersistsLogicalSourceForeignKey() {
        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);
        DataSource definition = dataSourceDefinition();
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(dataSourceRepository.findByCode("CORE_DB")).thenReturn(Optional.of(definition));
        when(projectRepository.findEnvironment("project-1", Environment.UAT))
                .thenReturn(
                        Optional.of(
                                new ProjectEnvironmentMapping(
                                        "env-uat", "project-1", Environment.UAT, true, NOW)));
        when(secretRefFactory.forMapping("WING_CORE", "UAT", "CORE_DB"))
                .thenReturn("projects/WING_CORE/environments/UAT/data-sources/CORE_DB");

        ProjectDataSourceResponse response =
                projectService.mapDataSource(
                        "project-1",
                        new MapDataSourceRequest(
                                Environment.UAT,
                                "CORE_DB",
                                "public",
                                "jdbc:postgresql://127.0.0.1:5433/core",
                                true));

        ArgumentCaptor<ProjectDataSourceMapping> captor =
                ArgumentCaptor.forClass(ProjectDataSourceMapping.class);
        verify(projectRepository).insertDataSource(captor.capture());
        assertEquals("source-1", captor.getValue().dataSourceId());
        assertEquals(DatabaseEngine.POSTGRESQL, captor.getValue().engine());
        assertEquals(captor.getValue().id(), response.projectDataSourceId());
    }

    @Test
    void testConnectionLoadsCredentialsFromVaultSecretReference() {
        ProjectDataSourceMapping mapping =
                dataSource(
                        "mapping-1",
                        "env-uat",
                        Environment.UAT,
                        "source-1",
                        "CORE_DB",
                        DatabaseEngine.POSTGRESQL);
        char[] password = "secret".toCharArray();
        when(projectRepository.findDataSource("project-1", "mapping-1"))
                .thenReturn(Optional.of(mapping));
        when(secretResolver.resolve("SECRET_CORE_DB"))
                .thenReturn(new DatabaseCredentials("app_user", password));
        DataSourceKey key = new DataSourceKey("WING_CORE", Environment.UAT, "CORE_DB");

        when(connectionTester.test(
                        key, DatabaseEngine.POSTGRESQL, mapping.jdbcUrl(), "app_user", password))
                .thenReturn(true);

        when(projectRepository.findById("project-1"))
                .thenReturn(
                        Optional.of(
                                new Project(
                                        "project-1",
                                        "WING_CORE",
                                        "Core Banking",
                                        "Core Team",
                                        ProjectStatus.ACTIVE,
                                        PromotionMode.SEQUENTIAL,
                                        "test-user",
                                        NOW,
                                        NOW)));

        // Act
        var response = projectService.testDataSourceConnection("project-1", "mapping-1");

        // Assert execution order
        InOrder ordered = inOrder(targetApprovalValidator, secretResolver, connectionTester);

        ordered.verify(targetApprovalValidator)
                .assertApproved(key, mapping.engine(), mapping.jdbcUrl());

        ordered.verify(secretResolver).resolve(mapping.secretRef());

        ordered.verify(connectionTester)
                .test(key, mapping.engine(), mapping.jdbcUrl(), "app_user", password);

        assertTrue(response.connected());
        assertEquals("mapping-1", response.projectDataSourceId());
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void testConnectionRejectsUnapprovedTargetBeforeResolvingCredentials() {

        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);

        ProjectDataSourceMapping mapping =
                dataSource(
                        "mapping-1",
                        "env-uat",
                        Environment.UAT,
                        "source-1",
                        "CORE_DB",
                        DatabaseEngine.POSTGRESQL);

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        when(projectRepository.findDataSource("project-1", "mapping-1"))
                .thenReturn(Optional.of(mapping));

        DataSourceKey key = new DataSourceKey("WING_CORE", Environment.UAT, "CORE_DB");

        doThrow(new TargetNotApprovedException())
                .when(targetApprovalValidator)
                .assertApproved(key, mapping.engine(), mapping.jdbcUrl());

        assertThrows(
                TargetNotApprovedException.class,
                () -> projectService.testDataSourceConnection("project-1", "mapping-1"));

        verifyNoInteractions(secretResolver, connectionTester);
    }

    @Test
    void mapDataSourceRejectsUnapprovedTargetBeforePersistence() {

        Project project =
                new Project(
                        "project-1",
                        "WING_CORE",
                        "Core Banking",
                        "Core Team",
                        ProjectStatus.ACTIVE,
                        PromotionMode.SEQUENTIAL,
                        "test-user",
                        NOW,
                        NOW);

        DataSource definition = dataSourceDefinition();

        String jdbcUrl = "jdbc:postgresql://unapproved.example:5432/core";

        DataSourceKey key = new DataSourceKey("WING_CORE", Environment.UAT, "CORE_DB");

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        when(dataSourceRepository.findByCode("CORE_DB")).thenReturn(Optional.of(definition));

        doThrow(new TargetNotApprovedException())
                .when(targetApprovalValidator)
                .assertApproved(key, DatabaseEngine.POSTGRESQL, jdbcUrl);

        MapDataSourceRequest request =
                new MapDataSourceRequest(Environment.UAT, "CORE_DB", "public", jdbcUrl, true);

        assertThrows(
                TargetNotApprovedException.class,
                () -> projectService.mapDataSource("project-1", request));

        verify(projectRepository, never()).insertDataSource(any(ProjectDataSourceMapping.class));

        verify(projectRepository, never()).touchProject(any(), any());

        verifyNoInteractions(secretRefFactory, secretResolver, connectionTester);
    }

    private static ProjectDataSourceMapping dataSource(
            String id,
            String envId,
            Environment environment,
            String sourceId,
            String code,
            DatabaseEngine engine) {
        return new ProjectDataSourceMapping(
                id,
                "project-1",
                envId,
                environment,
                sourceId,
                code,
                engine,
                "MWING",
                "jdbc:postgresql://127.0.0.1:5433/" + code.toLowerCase(),
                "SECRET_" + code,
                true,
                NOW,
                NOW);
    }

    private static DataSource dataSourceDefinition() {
        return new DataSource(
                DATA_SOURCE_ID,
                DATA_SOURCE_CODE,
                DATA_SOURCE_CODE,
                DatabaseEngine.POSTGRESQL,
                null,
                true,
                "test-user",
                NOW,
                NOW);
    }
}
