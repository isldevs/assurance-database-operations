package com.company.assurance.dbops.datasource.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.config.DataSourceRegistrationProperties;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.PoolProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties.ApprovedTarget;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.environment.Environment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TargetApprovalValidatorTests {
    private static final String APPROVED_URL = "jdbc:postgresql://127.0.0.1:1/demo";

    private static final DataSourceKey APPROVED_KEY =
            new DataSourceKey("DEMO", Environment.UAT, "CORE_DB");

    private static TargetApprovalValidator dynamicValidator() {
        return new TargetApprovalValidator(
                new TargetApprovalProperties(List.of(approval(Environment.UAT, true))),
                new DatabaseOperationsProperties(3, List.of()));
    }

    @Test
    void acceptsMatchingApproval() {
        assertDoesNotThrow(
                () ->
                        validator(
                                registration(APPROVED_URL),
                                List.of(approval(Environment.UAT, true))));
    }

    @Test
    void rejectsMissingApproval() {
        assertThrows(
                TargetNotApprovedException.class,
                () -> validator(registration(APPROVED_URL), List.of()));
    }

    @Test
    void rejectsDifferentUrl() {
        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        validator(
                                registration("jdbc:postgresql://127.0.0.1:2/other"),
                                List.of(approval(Environment.UAT, true))));
    }

    @Test
    void rejectsApprovalForDifferentEnvironment() {
        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        validator(
                                registration(APPROVED_URL),
                                List.of(approval(Environment.STAGE, true))));
    }

    @Test
    void rejectsUnconfirmedTarget() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        validator(
                                registration(APPROVED_URL),
                                List.of(approval(Environment.UAT, false))));
    }

    @Test
    void rejectsDuplicateApprovals() {
        ApprovedTarget target = approval(Environment.UAT, true);

        assertThrows(
                IllegalStateException.class,
                () -> validator(registration(APPROVED_URL), List.of(target, target)));
    }

    private static TargetApprovalValidator validator(
            DataSourceRegistrationProperties registration, List<ApprovedTarget> approvals) {
        return new TargetApprovalValidator(
                new TargetApprovalProperties(approvals),
                new DatabaseOperationsProperties(3, List.of(registration)));
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
                new PoolProperties(1, 0, 3000, 1000, 60000, 1800000, 0));
    }

    @Test
    void acceptsExactDynamicTarget() {
        assertDoesNotThrow(
                () ->
                        dynamicValidator()
                                .assertApproved(
                                        APPROVED_KEY, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    @Test
    void rejectsUnknownProject() {
        DataSourceKey key = new DataSourceKey("UNKNOWN_PROJECT", Environment.UAT, "CORE_DB");

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        dynamicValidator()
                                .assertApproved(key, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    @Test
    void rejectsUnauthorizedEnvironment() {
        DataSourceKey key = new DataSourceKey("DEMO", Environment.STAGE3, "CORE_DB");

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        dynamicValidator()
                                .assertApproved(key, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    @Test
    void rejectsUnknownDataSource() {
        DataSourceKey key = new DataSourceKey("DEMO", Environment.UAT, "PAYMENT_DB");

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        dynamicValidator()
                                .assertApproved(key, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    @Test
    void rejectsIncorrectEngine() {
        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        dynamicValidator()
                                .assertApproved(APPROVED_KEY, DatabaseEngine.ORACLE, APPROVED_URL));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:postgresql://unapproved.invalid:1/demo",
                "jdbc:postgresql://127.0.0.1:5432/demo",
                "jdbc:postgresql://127.0.0.1:1/other",
                "jdbc:postgresql://127.0.0.1:1/demo?sslmode=disable",
                "jdbc:postgresql://127.0.0.1:1/demo?user=admin",
                "jdbc:postgresql://127.0.0.1:1,database.invalid:5432/demo",
                "jdbc:postgresql://localhost:1/demo",
                "jdbc:postgresql://127.0.0.1:1/demo ",
                ""
            })
    void rejectsModifiedJdbcUrl(String jdbcUrl) {
        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        dynamicValidator()
                                .assertApproved(APPROVED_KEY, DatabaseEngine.POSTGRESQL, jdbcUrl));
    }

    @Test
    void rejectsDynamicTargetWhenPolicyIsEmpty() {
        TargetApprovalValidator validator =
                new TargetApprovalValidator(
                        new TargetApprovalProperties(List.of()),
                        new DatabaseOperationsProperties(3, List.of()));

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        validator.assertApproved(
                                APPROVED_KEY, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    @Test
    void rejectsRemovedApprovalAfterPolicyReinitialization() {
        TargetApprovalValidator previousPolicy = dynamicValidator();

        assertDoesNotThrow(
                () ->
                        previousPolicy.assertApproved(
                                APPROVED_KEY, DatabaseEngine.POSTGRESQL, APPROVED_URL));

        TargetApprovalValidator updatedPolicy =
                new TargetApprovalValidator(
                        new TargetApprovalProperties(List.of()),
                        new DatabaseOperationsProperties(3, List.of()));

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        updatedPolicy.assertApproved(
                                APPROVED_KEY, DatabaseEngine.POSTGRESQL, APPROVED_URL));
    }

    private static ApprovedTarget approval(Environment environment, boolean confirmed) {
        return new ApprovedTarget(
                "DEMO", environment, "CORE_DB", DatabaseEngine.POSTGRESQL, APPROVED_URL, confirmed);
    }
}
