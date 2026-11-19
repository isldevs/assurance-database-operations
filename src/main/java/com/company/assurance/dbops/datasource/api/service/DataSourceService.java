package com.company.assurance.dbops.datasource.api.service;

import com.company.assurance.dbops.datasource.api.data.request.DataSourceRequest;
import com.company.assurance.dbops.datasource.api.data.response.DataSourceResponse;
import com.company.assurance.dbops.datasource.api.entity.DataSource;
import com.company.assurance.dbops.datasource.api.exception.DataSourceNotFoundException;
import com.company.assurance.dbops.datasource.api.repository.DataSourceRepository;
import com.company.assurance.dbops.security.CustomSecurityContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final CustomSecurityContextHolder securityContextHolder;
    private final Clock clock;

    public DataSourceService(
            DataSourceRepository repository,
            CustomSecurityContextHolder securityContextHolder,
            Clock clock) {
        this.repository = repository;
        this.securityContextHolder = securityContextHolder;
        this.clock = clock;
    }

    @Transactional
    public DataSourceResponse create(DataSourceRequest request) {
        String code = normalizeCode(request.dataSourceCode());

        Instant now = clock.instant();
        DataSource definition =
                new DataSource(
                        UUID.randomUUID().toString(),
                        code,
                        request.dataSourceName().strip(),
                        request.engine(),
                        blankToNull(request.description()),
                        request.enabledOrDefault(),
                        securityContextHolder.currentUser(),
                        now,
                        now);
        repository.insert(definition);
        return toResponse(definition);
    }

    @Transactional(readOnly = true)
    public List<DataSourceResponse> list() {
        return repository.findAll().stream().map(DataSourceService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DataSourceResponse get(String id) {
        return repository
                .findById(id)
                .map(DataSourceService::toResponse)
                .orElseThrow(() -> new DataSourceNotFoundException(id));
    }

    private static DataSourceResponse toResponse(DataSource definition) {
        return new DataSourceResponse(
                definition.id(),
                definition.dataSourceCode(),
                definition.dataSourceName(),
                definition.engine(),
                definition.description(),
                definition.enabled(),
                definition.createdBy());
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dataSourceCode is required");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
