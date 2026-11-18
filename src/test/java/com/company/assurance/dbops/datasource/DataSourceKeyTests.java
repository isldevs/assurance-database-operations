package com.company.assurance.dbops.datasource;

import com.company.assurance.dbops.environment.Environment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DataSourceKeyTests {

    @Test
    void normalizesCodesForConsistentResolution() {
        DataSourceKey actual = new DataSourceKey(
                "core_banking",
                Environment.UAT,
                "core_db"
        );

        DataSourceKey expected = new DataSourceKey(
                "CORE_BANKING",
                Environment.UAT,
                "CORE_DB"
        );

        assertEquals(actual, expected);
    }

    @Test
    void keepEnvironmentsSeparate() {
        DataSourceKey uat = new DataSourceKey(
                "CORE_BANKING",
                Environment.UAT,
                "CORE_DB"
        );

        DataSourceKey stage = new DataSourceKey(
                "CORE_BANKING",
                Environment.STAGE,
                "CORE_DB"
        );

        assertNotEquals(uat, stage);
    }

    @Test
    void rejectsInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> new DataSourceKey("../CORE", Environment.UAT, "CORE_DB"));
    }

}
