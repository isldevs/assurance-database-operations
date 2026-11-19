package com.company.assurance.dbops.datasource.api.repository;

import com.company.assurance.dbops.datasource.api.entity.DataSource;
import com.company.assurance.dbops.datasource.api.exception.DataSourceAlreadyExistsException;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Uses {@link JdbcClient} to keep central datasource metadata access explicit and parameterized,
 * with Flyway as the schema authority. JPA entity state and relationship management add no value to
 * these simple, security-sensitive metadata operations. Use {@code JdbcTemplate} / JDBC in the
 * worker for operational database execution.
 */
@Repository
public class DataSourceRepository {

    private final JdbcClient jdbcClient;

    public DataSourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<DataSource> findById(String id) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, data_source_code, data_source_name, engine, description,
                               enabled, created_by, created_at, updated_at
                        FROM db_data_source
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    public Optional<DataSource> findByCode(String dataSourceCode) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, data_source_code, data_source_name, engine, description,
                               enabled, created_by, created_at, updated_at
                        FROM db_data_source
                        WHERE data_source_code = :dataSourceCode
                        """)
                .param("dataSourceCode", dataSourceCode)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    public List<DataSource> findAll() {
        return jdbcClient
                .sql(
                        """
                        SELECT id, data_source_code, data_source_name, engine, description,
                               enabled, created_by, created_at, updated_at
                        FROM db_data_source
                        ORDER BY data_source_code
                        """)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    public void insert(DataSource dataSource) {
        try {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO db_data_source
                                (id, data_source_code, data_source_name, engine, description,
                                 enabled, created_by, created_at, updated_at)
                            VALUES
                                (:id, :dataSourceCode, :dataSourceName, :engine, :description,
                                 :enabled, :createdBy, :createdAt, :updatedAt)
                            """)
                    .param("id", dataSource.id())
                    .param("dataSourceCode", dataSource.dataSourceCode())
                    .param("dataSourceName", dataSource.dataSourceName())
                    .param("engine", dataSource.engine().name())
                    .param("description", dataSource.description())
                    .param("enabled", dataSource.enabled())
                    .param("createdBy", dataSource.createdBy())
                    .param("createdAt", Timestamp.from(dataSource.createdAt()))
                    .param("updatedAt", Timestamp.from(dataSource.updatedAt()))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new DataSourceAlreadyExistsException(dataSource.dataSourceCode());
        }
    }

    private static DataSource map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DataSource(
                rs.getString("id"),
                rs.getString("data_source_code"),
                rs.getString("data_source_name"),
                DatabaseEngine.valueOf(rs.getString("engine")),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
