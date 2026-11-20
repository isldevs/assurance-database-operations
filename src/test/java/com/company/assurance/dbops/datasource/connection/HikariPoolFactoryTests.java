package com.company.assurance.dbops.datasource.connection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.PoolProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.datasource.validation.TargetApprovalValidator;
import com.company.assurance.dbops.datasource.validation.TargetNotApprovedException;
import com.company.assurance.dbops.environment.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class HikariPoolFactoryTests {

    @Test
    void rejectsUnapprovedTargetBeforeCreatingPool() {

        TargetApprovalValidator validator =
                new TargetApprovalValidator(
                        new TargetApprovalProperties(List.of()),
                        new DatabaseOperationsProperties(3, List.of()));

        MeterRegistry meterRegistry = mock(MeterRegistry.class);

        HikariPoolFactory factory = new HikariPoolFactory(validator, meterRegistry);

        DataSourceRegistrationProperties registration =
                new DataSourceRegistrationProperties(
                        "DEMO",
                        Environment.UAT,
                        "CORE_DB",
                        DatabaseEngine.POSTGRESQL,
                        "jdbc:postgresql://unapproved.invalid:5432/demo",
                        "test-user",
                        "test-fixture-not-a-real-secret",
                        true,
                        new PoolProperties(1, 0, 3000, 1000, 60000, 1800000, 0));

        assertThrows(TargetNotApprovedException.class, () -> factory.create(registration).close());

        verifyNoInteractions(meterRegistry);
    }
}
