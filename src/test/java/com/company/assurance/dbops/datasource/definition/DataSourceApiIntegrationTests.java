package com.company.assurance.dbops.datasource.definition;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class DataSourceApiIntegrationTests {

    @Autowired private WebApplicationContext webApplicationContext;

    @Test
    void createsAndListsCentralDataSource() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(
                        post("/api/v1/data-sources")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "dataSourceCode": "PERSONA_DB",
                                          "dataSourceName": "Persona Database",
                                          "engine": "POSTGRESQL",
                                          "description": "Reusable persona database definition"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dataSourceCode").value("PERSONA_DB"))
                .andExpect(jsonPath("$.data.createdBy").value("test-user"))
                .andExpect(
                        jsonPath("$.data.description")
                                .value("Reusable persona database definition"))
                .andExpect(jsonPath("$.data.jdbcUrl").doesNotExist());

        mockMvc.perform(get("/api/v1/data-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dataSourceCode").exists());
    }
}
