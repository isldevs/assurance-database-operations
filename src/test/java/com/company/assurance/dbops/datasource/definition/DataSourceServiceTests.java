package com.company.assurance.dbops.datasource.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.assurance.dbops.datasource.api.data.request.DataSourceRequest;
import com.company.assurance.dbops.datasource.api.entity.DataSource;
import com.company.assurance.dbops.datasource.api.repository.DataSourceRepository;
import com.company.assurance.dbops.datasource.api.service.DataSourceService;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.security.CustomSecurityContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");

    @Mock private DataSourceRepository repository;
    @Mock private CustomSecurityContextHolder securityContextHolder;

    private DataSourceService service;

    @BeforeEach
    void setUp() {
        service =
                new DataSourceService(
                        repository, securityContextHolder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesCodeAndHidesNoSecretsFromResponse() {
        when(securityContextHolder.currentUser()).thenReturn("test-user");
        var response =
                service.create(
                        new DataSourceRequest(
                                "core_db",
                                "Core Banking",
                                DatabaseEngine.POSTGRESQL,
                                "Reusable core database",
                                null));
        ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
        verify(repository).insert(captor.capture());

        assertEquals("CORE_DB", captor.getValue().dataSourceCode());
        assertEquals("test-user", captor.getValue().createdBy());
        assertEquals("CORE_DB", response.dataSourceCode());
        assertEquals("Reusable core database", response.description());
        assertEquals("test-user", response.createdBy());
    }

    @Test
    void createUsesSchemaFieldsOnly() {
        when(securityContextHolder.currentUser()).thenReturn("test-user");

        service.create(
                new DataSourceRequest(
                        "CORE_DB", "Core Banking", DatabaseEngine.POSTGRESQL, null, false));

        ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
        verify(repository).insert(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().enabled());
        assertEquals(null, captor.getValue().description());
    }
}
