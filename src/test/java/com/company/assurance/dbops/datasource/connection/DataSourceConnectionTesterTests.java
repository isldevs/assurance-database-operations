package com.company.assurance.dbops.datasource.connection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.config.DatabaseOperationsProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties;
import com.company.assurance.dbops.datasource.config.TargetApprovalProperties.ApprovedTarget;
import com.company.assurance.dbops.datasource.engine.DatabaseEngine;
import com.company.assurance.dbops.datasource.validation.TargetApprovalValidator;
import com.company.assurance.dbops.datasource.validation.TargetNotApprovedException;
import com.company.assurance.dbops.environment.Environment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceConnectionTesterTests {

    private static final DataSourceKey KEY =
            new DataSourceKey("WING_CORE", Environment.UAT, "CORE_DB");

    private static final String APPROVED_URL = "jdbc:postgresql://127.0.0.1:5433/core";

    private DataSourceConnectionTester connectionTester;

    @BeforeEach
    void setUp() {

        TargetApprovalProperties policy =
                new TargetApprovalProperties(
                        List.of(
                                new ApprovedTarget(
                                        "WING_CORE",
                                        Environment.UAT,
                                        "CORE_DB",
                                        DatabaseEngine.POSTGRESQL,
                                        APPROVED_URL,
                                        true)));

        DatabaseOperationsProperties operations = mock(DatabaseOperationsProperties.class);

        when(operations.registrations()).thenReturn(List.of());

        TargetApprovalValidator validator = new TargetApprovalValidator(policy, operations);

        connectionTester = new DataSourceConnectionTester(validator);
    }

    @Test
    void rejectsUnapprovedJdbcUrl() {

        char[] password = "test-password".toCharArray();

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        connectionTester.test(
                                KEY,
                                DatabaseEngine.POSTGRESQL,
                                "jdbc:postgresql://unapproved.invalid:5432/core",
                                "test-user",
                                password));

        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void rejectsUnapprovedEngine() {

        char[] password = "test-password".toCharArray();

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        connectionTester.test(
                                KEY, DatabaseEngine.ORACLE, APPROVED_URL, "test-user", password));

        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void rejectsUnapprovedDataSourceKey() {

        DataSourceKey unapprovedKey =
                new DataSourceKey("WING_PAYMENT", Environment.UAT, "PAYMENT_DB");

        char[] password = "test-password".toCharArray();

        assertThrows(
                TargetNotApprovedException.class,
                () ->
                        connectionTester.test(
                                unapprovedKey,
                                DatabaseEngine.POSTGRESQL,
                                APPROVED_URL,
                                "test-user",
                                password));

        assertArrayEquals(new char[password.length], password);
    }
}
