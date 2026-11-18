package com.company.assurance.dbops.datasource.validation;

import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.PoolProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties.ApprovedTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TargetApprovalValidatorTests {
    private static final String APPROVED_URL = "jdbc:postgresql://127.0.0.1:1/demo";

    @Test
    void acceptsMatchingApproval() {
        assertDoesNotThrow(() -> validator(
                registration(APPROVED_URL),
                List.of(approval(Environment.UAT, true))
        ));
    }

    @Test
    void rejectsMissingApproval() {
        assertThrows(
                TargetNotApprovedException.class,
                () -> validator(
                        registration(APPROVED_URL),
                        List.of()
                )
        );
    }

    @Test
    void rejectsDifferentUrl() {
        assertThrows(
                TargetNotApprovedException.class,
                () -> validator(
                        registration("jdbc:postgresql://127.0.0.1:2/other"),
                        List.of(approval(Environment.UAT, true))
                )
        );
    }

    @Test
    void rejectsApprovalForDifferentEnvironment() {
        assertThrows(
                TargetNotApprovedException.class,
                () -> validator(
                        registration(APPROVED_URL),
                        List.of(approval(Environment.STAGE, true))
                )
        );
    }

    @Test
    void rejectsUnconfirmedTarget() {
        assertThrows(
                IllegalStateException.class,
                () -> validator(
                        registration(APPROVED_URL),
                        List.of(approval(Environment.UAT, false))
                )
        );
    }

    @Test
    void rejectsDuplicateApprovals() {
        ApprovedTarget target = approval(Environment.UAT, true);

        assertThrows(
                IllegalStateException.class,
                () -> validator(
                        registration(APPROVED_URL),
                        List.of(target, target)
                )
        );
    }

    private static TargetApprovalValidator validator(DataSourceRegistrationProperties registration, List<ApprovedTarget> approvals) {
        return new TargetApprovalValidator(
                new TargetApprovalProperties(approvals),
                new DatabaseOperationsProperties(
                        3,
                        List.of(registration)
                )
        );
    }

    private static DataSourceRegistrationProperties registration(String jdbcUrl) {
        return new DataSourceRegistrationProperties(
                "DEMO",
                Environment.UAT,
                "CORE_DB",
                DatabaseEngine.POSTGRESQL,
                jdbcUrl,
                "test",
                "test-fixture-not-a-real-secret",
                true,
                new PoolProperties(
                        1,
                        0,
                        3000,
                        1000,
                        60000,
                        1800000,
                        0
                )
        );
    }

    private static ApprovedTarget approval(Environment environment, boolean confirmed) {
        return new ApprovedTarget(
                "DEMO",
                environment,
                "CORE_DB",
                DatabaseEngine.POSTGRESQL,
                APPROVED_URL,
                confirmed
        );
    }
}
