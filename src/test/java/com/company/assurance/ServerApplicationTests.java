package com.company.assurance;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

    @Autowired private JdbcClient jdbcClient;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // Verifies the application does not require a default datasource.
        assertNotNull(jdbcClient);
        assertNotNull(jdbcTemplate);
    }
}
