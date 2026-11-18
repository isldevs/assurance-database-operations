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
                        .andReturn();

        String projectId =
                objectMapper
                        .readTree(created.getResponse().getContentAsString())
                        .path("data")
                        .path("projectId")
                        .asText();

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
                        post("/api/v1/projects/{projectId}/data-sources", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "environment": "UAT",
                                  "dataSourceCode": "CORE_DB",
                                  "engine": "POSTGRESQL",
                                  "schemaName": "core",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/uat_core",
                                  "username": "dbops_app",
                                  "secretRef": "UAT_CORE_DB_PASSWORD"
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
                                  "dataSourceCode": "PERSONA_DB",
                                  "engine": "POSTGRESQL",
                                  "schemaName": "persona",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/uat_persona",
                                  "username": "dbops_app",
                                  "secretRef": "UAT_PERSONA_DB_PASSWORD"
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
                                  "dataSourceCode": "CORE_DB",
                                  "engine": "POSTGRESQL",
                                  "schemaName": "core",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5433/stage_core",
                                  "username": "dbops_app",
                                  "secretRef": "STAGE_CORE_DB_PASSWORD"
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

        assertEquals("UAT", environments.get(0).path("environment").asText());
        assertEquals(2, environments.get(0).path("dataSources").size());
        assertEquals("STAGE", environments.get(1).path("environment").asText());
        assertEquals(1, environments.get(1).path("dataSources").size());
        assertTrue(environments.toString().contains("PERSONA_DB"));
        assertTrue(environments.toString().contains("CORE_DB"));
    }
}
