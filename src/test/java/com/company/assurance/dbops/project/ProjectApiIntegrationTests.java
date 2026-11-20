package com.company.assurance.dbops.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class ProjectApiIntegrationTests {

    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void migrationMovesMappedDataSourceUsernameOutOfMetadataSchema() {
        Integer usernameColumnCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = 'PUBLIC'
                          AND TABLE_NAME = 'DB_PROJECT_ENV_DATASOURCE'
                          AND COLUMN_NAME = 'USERNAME'
                        """,
                        Integer.class);

        assertEquals(0, usernameColumnCount);
    }

    @Test
    void createsProjectWithConfiguredTestActorAndDefaultPromotionMode() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(
                        post("/api/v1/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "projectCode": "ACTOR_TEST",
                                  "projectName": "Actor Test Project",
                                  "ownerName": "Test Team"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.promotionMode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.data.createdBy").value("test-user"));
    }

    @Test
    void supportsOneProjectWithMultipleEnvironmentsAndDataSources() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/projects")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {
                                  "projectCode": "WING_CORE",
                                  "projectName": "Core Banking Platform",
                                  "ownerName": "Core Banking Team"
                                }
                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.projectCode").value("WING_CORE"))
                        .andExpect(jsonPath("$.data.promotionMode").value("SEQUENTIAL"))
                        .andExpect(jsonPath("$.data.createdBy").value("test-user"))
                        .andReturn();

        String projectId =
                objectMapper
                        .readTree(created.getResponse().getContentAsString())
                        .path("data")
                        .path("projectId")
                        .asString();

        mockMvc.perform(
                        post("/api/v1/projects/{projectId}/environments", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                { "environment": "UAT" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/projects/{projectId}/environments", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                { "environment": "STAGE" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/data-sources")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "dataSourceCode": "CORE_DB_WING",
                                  "dataSourceName": "Core Database",
                                  "engine": "POSTGRESQL",
                                  "description": "Core banking database"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/data-sources")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "dataSourceCode": "PERSONA_DB_WING",
                                  "dataSourceName": "Persona Database",
                                  "engine": "POSTGRESQL"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/projects/{projectId}/data-sources", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "environment": "UAT",
                                  "dataSourceCode": "CORE_DB_WING",
                                  "schemaName": "core",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/uat_core",
                                  "secretRef": "projects/WING_CORE/environments/UAT/data-sources/CORE_DB_WING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").doesNotExist())
                .andExpect(jsonPath("$.data.jdbcUrl").doesNotExist());

        mockMvc.perform(
                        post("/api/v1/projects/{projectId}/data-sources", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "environment": "UAT",
                                  "dataSourceCode": "PERSONA_DB_WING",
                                  "schemaName": "persona",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/uat_persona",
                                  "secretRef": "projects/WING_CORE/environments/UAT/data-sources/PERSONA_DB_WING"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/projects/{projectId}/data-sources", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "environment": "STAGE",
                                  "dataSourceCode": "CORE_DB_WING",
                                  "schemaName": "core",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/stage_core",
                                  "secretRef": "projects/WING_CORE/environments/STAGE/data-sources/CORE_DB_WING"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult detailResult =
                mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.environments.length()").value(2))
                        .andReturn();

        JsonNode environments =
                objectMapper
                        .readTree(detailResult.getResponse().getContentAsString())
                        .path("data")
                        .path("environments");

        assertEquals("UAT", environments.get(0).path("environment").asString());
        assertEquals(2, environments.get(0).path("dataSources").size());
        assertEquals("STAGE", environments.get(1).path("environment").asString());
        assertEquals(1, environments.get(1).path("dataSources").size());
        assertTrue(environments.toString().contains("PERSONA_DB"));
        assertTrue(environments.toString().contains("CORE_DB"));
    }
}
