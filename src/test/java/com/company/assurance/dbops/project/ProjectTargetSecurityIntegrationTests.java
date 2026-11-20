package com.company.assurance.dbops.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.assurance.dbops.common.api.CorrelationIdFilter;
import com.company.assurance.dbops.datasource.DataSourceSecretResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectTargetSecurityIntegrationTests {

    private static final String APPROVED_URL = "jdbc:postgresql://127.0.0.1:1/security_test";

    private static final String UNAPPROVED_URL =
            "jdbc:postgresql://unapproved.invalid:5432/security_test";

    private static final String CORRELATION_ID = "79c3e403-22f2-438f-a987-483108a0a721";

    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired private CorrelationIdFilter correlationIdFilter;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private DataSourceSecretResolver secretResolver;

    private MockMvc mockMvc;

    private String projectId;

    @BeforeEach
    void setUp() throws Exception {

        mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext)
                        .addFilters(correlationIdFilter)
                        .build();

        MvcResult projectResult =
                mockMvc.perform(
                                post("/api/v1/projects")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "projectCode": "SECURITY_TEST",
                                                  "projectName": "Security Test",
                                                  "ownerName": "Security Team"
                                                }
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn();

        projectId =
                objectMapper
                        .readTree(projectResult.getResponse().getContentAsString())
                        .path("data")
                        .path("projectId")
                        .asString();

        mockMvc.perform(
                        post("/api/v1/projects/{id}/environments", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "environment": "UAT"
                                        }
                                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/data-sources")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "dataSourceCode": "SECURITY_CORE_DB",
                                          "dataSourceName": "Security Test DB",
                                          "engine": "POSTGRESQL"
                                        }
                                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void approvedTargetCanBeMapped() throws Exception {

        mockMvc.perform(
                        post("/api/v1/projects/{id}/data-sources", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mappingRequest(APPROVED_URL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jdbcUrl").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        assertEquals(1L, countProjectMappings());

        verifyNoInteractions(secretResolver);
    }

    @Test
    void unapprovedTargetReturnsForbiddenWithoutPersistence() throws Exception {

        mockMvc.perform(
                        post("/api/v1/projects/{id}/data-sources", projectId)
                                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mappingRequest(UNAPPROVED_URL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TARGET_NOT_APPROVED"))
                .andExpect(jsonPath("$.error.message").value("Database target is not approved"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(header().string(CorrelationIdFilter.HEADER, CORRELATION_ID))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(0L, countProjectMappings());

        verifyNoInteractions(secretResolver);
    }

    @Test
    void storedUnapprovedTargetCannotRetrieveVaultCredentials() throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/projects/{id}/data-sources", projectId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mappingRequest(APPROVED_URL)))
                        .andExpect(status().isCreated())
                        .andReturn();

        String mappingId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .path("data")
                        .path("projectDataSourceId")
                        .asString();

        // Simulate a stale or tampered metadata record.
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE db_project_env_datasource
                        SET jdbc_url = ?
                        WHERE id = ?
                        """,
                        UNAPPROVED_URL,
                        mappingId);

        assertEquals(1, updated);

        mockMvc.perform(
                        post(
                                        "/api/v1/projects/{id}/data-sources/{mappingId}/test-connection",
                                        projectId,
                                        mappingId)
                                .header(CorrelationIdFilter.HEADER, CORRELATION_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TARGET_NOT_APPROVED"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));

        verifyNoInteractions(secretResolver);
    }

    private long countProjectMappings() {

        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM db_project_env_datasource ds
                        JOIN db_project_environment env
                          ON env.id = ds.project_environment_id
                        WHERE env.project_id = ?
                        """,
                        Long.class,
                        projectId);

        return count == null ? 0L : count;
    }

    private static String mappingRequest(String jdbcUrl) {
        return """
                {
                  "environment": "UAT",
                  "dataSourceCode": "SECURITY_CORE_DB",
                  "schemaName": "public",
                  "jdbcUrl": "%s",
                  "enabled": true
                }
                """
                .formatted(jdbcUrl);
    }
}
