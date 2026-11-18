package com.company.assurance.dbops.project.repository;

import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import com.company.assurance.dbops.project.entity.Project;
import com.company.assurance.dbops.project.entity.ProjectDataSourceMapping;
import com.company.assurance.dbops.project.entity.ProjectEnvironmentMapping;
import com.company.assurance.dbops.project.entity.ProjectStatus;
import com.company.assurance.dbops.project.exception.DuplicateProjectMappingException;
import com.company.assurance.dbops.project.exception.ProjectAlreadyExistsException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {

    private final JdbcClient jdbcClient;

    public ProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Project> findById(String projectId) {
        return jdbcClient.sql("""
                        SELECT id, project_code, project_name, owner_name, status, created_at, updated_at
                        FROM db_project
                        WHERE id = :id
                        """)
                .param("id", projectId)
                .query((rs, rowNum) -> new Project(
                        rs.getString("id"),
                        rs.getString("project_code"),
                        rs.getString("project_name"),
                        rs.getString("owner_name"),
                        ProjectStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }

    public Optional<Project> findByCode(String projectCode) {
        return jdbcClient.sql("""
                        SELECT id, project_code, project_name, owner_name, status, created_at, updated_at
                        FROM db_project
                        WHERE project_code = :projectCode
                        """)
                .param("projectCode", projectCode)
                .query((rs, rowNum) -> new Project(
                        rs.getString("id"),
                        rs.getString("project_code"),
                        rs.getString("project_name"),
                        rs.getString("owner_name"),
                        ProjectStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .optional();
    }

    public List<Project> findAll() {
        return jdbcClient.sql("""
                        SELECT id, project_code, project_name, owner_name, status, created_at, updated_at
                        FROM db_project
                        ORDER BY project_code
                        """)
                .query((rs, rowNum) -> new Project(
                        rs.getString("id"),
                        rs.getString("project_code"),
                        rs.getString("project_name"),
                        rs.getString("owner_name"),
                        ProjectStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .list();
    }

    public void insert(Project project) {
        try {
            jdbcClient.sql("""
                            INSERT INTO db_project
                                (id, project_code, project_name, owner_name, status, created_at, updated_at)
                            VALUES
                                (:id, :projectCode, :projectName, :ownerName, :status, :createdAt, :updatedAt)
                            """)
                    .param("id", project.id())
                    .param("projectCode", project.projectCode())
                    .param("projectName", project.projectName())
                    .param("ownerName", project.ownerName())
                    .param("status", project.status().name())
                    .param("createdAt", Timestamp.from(project.createdAt()))
                    .param("updatedAt", Timestamp.from(project.updatedAt()))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new ProjectAlreadyExistsException(project.projectCode());
        }
    }

    public Optional<ProjectEnvironmentMapping> findEnvironment(String projectId, Environment environment) {
        return jdbcClient.sql("""
                        SELECT id, project_id, environment_code, enabled, created_at
                        FROM db_project_environment
                        WHERE project_id = :projectId AND environment_code = :environment
                        """)
                .param("projectId", projectId)
                .param("environment", environment.name())
                .query((rs, rowNum) -> new ProjectEnvironmentMapping(
                        rs.getString("id"),
                        rs.getString("project_id"),
                        Environment.valueOf(rs.getString("environment_code")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public List<ProjectEnvironmentMapping> findEnvironments(String projectId) {
        return jdbcClient.sql("""
                        SELECT pe.id, pe.project_id, pe.environment_code, pe.enabled, pe.created_at
                        FROM db_project_environment pe
                        INNER JOIN db_environment e ON e.code = pe.environment_code
                        WHERE pe.project_id = :projectId
                        ORDER BY e.sort_order
                        """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new ProjectEnvironmentMapping(
                        rs.getString("id"),
                        rs.getString("project_id"),
                        Environment.valueOf(rs.getString("environment_code")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    public void insertEnvironment(ProjectEnvironmentMapping mapping) {
        try {
            jdbcClient.sql("""
                            INSERT INTO db_project_environment
                                (id, project_id, environment_code, enabled, created_at)
                            VALUES
                                (:id, :projectId, :environment, :enabled, :createdAt)
                            """)
                    .param("id", mapping.id())
                    .param("projectId", mapping.projectId())
                    .param("environment", mapping.environment().name())
                    .param("enabled", mapping.enabled())
                    .param("createdAt", Timestamp.from(mapping.createdAt()))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new DuplicateProjectMappingException(
                    "Environment already mapped: " + mapping.environment().name()
            );
        }
    }

    public List<ProjectDataSourceMapping> findDataSources(String projectId) {
        return jdbcClient.sql("""
                        SELECT ds.id,
                               pe.project_id,
                               ds.project_environment_id,
                               pe.environment_code,
                               ds.data_source_code,
                               ds.engine,
                               ds.schema_name,
                               ds.jdbc_url,
                               ds.username,
                               ds.secret_ref,
                               ds.enabled,
                               ds.created_at,
                               ds.updated_at
                        FROM db_project_env_datasource ds
                        INNER JOIN db_project_environment pe ON pe.id = ds.project_environment_id
                        INNER JOIN db_environment e ON e.code = pe.environment_code
                        WHERE pe.project_id = :projectId
                        ORDER BY e.sort_order, ds.data_source_code
                        """)
                .param("projectId", projectId)
                .query((rs, rowNum) -> mapDataSource(rs))
                .list();
    }

    public void insertDataSource(ProjectDataSourceMapping mapping) {
        try {
            jdbcClient.sql("""
                            INSERT INTO db_project_env_datasource
                                (id, project_environment_id, data_source_code, engine, schema_name,
                                 jdbc_url, username, secret_ref, enabled, created_at, updated_at)
                            VALUES
                                (:id, :projectEnvironmentId, :dataSourceCode, :engine, :schemaName,
                                 :jdbcUrl, :username, :secretRef, :enabled, :createdAt, :updatedAt)
                            """)
                    .param("id", mapping.id())
                    .param("projectEnvironmentId", mapping.projectEnvironmentId())
                    .param("dataSourceCode", mapping.dataSourceCode())
                    .param("engine", mapping.engine().name())
                    .param("schemaName", mapping.schemaName())
                    .param("jdbcUrl", mapping.jdbcUrl())
                    .param("username", mapping.username())
                    .param("secretRef", mapping.secretRef())
                    .param("enabled", mapping.enabled())
                    .param("createdAt", Timestamp.from(mapping.createdAt()))
                    .param("updatedAt", Timestamp.from(mapping.updatedAt()))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new DuplicateProjectMappingException(
                    "Data source already mapped for environment "
                            + mapping.environment().name()
                            + ": "
                            + mapping.dataSourceCode()
            );
        }
    }

    @Transactional
    public void touchProject(String projectId, Instant updatedAt) {
        jdbcClient.sql("""
                        UPDATE db_project
                        SET updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("id", projectId)
                .param("updatedAt", Timestamp.from(updatedAt))
                .update();
    }

    private static ProjectDataSourceMapping mapDataSource(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProjectDataSourceMapping(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("project_environment_id"),
                Environment.valueOf(rs.getString("environment_code")),
                rs.getString("data_source_code"),
                DatabaseEngine.valueOf(rs.getString("engine")),
                rs.getString("schema_name"),
                rs.getString("jdbc_url"),
                rs.getString("username"),
                rs.getString("secret_ref"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
