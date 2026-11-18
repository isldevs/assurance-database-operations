package com.company.assurance.dbops.datasource.connection;

import com.company.assurance.dbops.datasource.DataSourceKey;
import com.company.assurance.dbops.datasource.registry.DataSourceRegistry;
import com.company.assurance.dbops.datasource.registry.RegisteredDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class DataSourceStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DataSourceStartUp.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 3;
    private static final String HEADER = "---------------- DATABASE CONNECTIONS ----------------";
    private static final String SEPARATOR = "-".repeat(HEADER.length());

    private final DataSource dataSource;
    private final DataSourceRegistry registry;

    public DataSourceStartUp(DataSource dataSource, DataSourceRegistry registry) {
        this.dataSource = dataSource;
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        report();
    }

    private void report() {
        List<ConnectionStatus> results = new ArrayList<>();
        results.add(checkConnection("POSTGRES", "POSTGRESQL", "UAT", true, dataSource));

        registry.keys().stream()
                .sorted(
                        Comparator.comparing(DataSourceKey::projectCode)
                                .thenComparing(DataSourceKey::environment)
                                .thenComparing(DataSourceKey::dataSourceCode))
                .forEach(
                        key -> {
                            RegisteredDataSource registration = registry.resolve(key);

                            results.add(
                                    checkConnection(
                                            key.dataSourceCode(),
                                            registration.engine().name(),
                                            key.environment().name(),
                                            false,
                                            registration.dataSource()));
                        });

        printReport(results);
    }

    private ConnectionStatus checkConnection(
            String name, String engine, String description, boolean metadata, DataSource source) {
        long startedAt = System.nanoTime();

        try (Connection connection = source.getConnection()) {

            boolean valid = connection.isValid(VALIDATION_TIMEOUT_SECONDS);

            return new ConnectionStatus(
                    name,
                    description,
                    metadata,
                    valid,
                    elapsedMillis(startedAt),
                    valid ? null : "Connection validation failed",
                    null,
                    null);

        } catch (SQLException ex) {

            return new ConnectionStatus(
                    name,
                    description,
                    metadata,
                    false,
                    elapsedMillis(startedAt),
                    DataSourceErrorResolver.resolve(engine, ex),
                    ex.getSQLState(),
                    ex.getErrorCode());

        } catch (RuntimeException ex) {

            return new ConnectionStatus(
                    name,
                    description,
                    metadata,
                    false,
                    elapsedMillis(startedAt),
                    "Unexpected datasource error: " + ex.getClass().getSimpleName(),
                    null,
                    null);
        }
    }

    private void printReport(List<ConnectionStatus> results) {

        long connected = results.stream().filter(ConnectionStatus::connected).count();

        long unavailable = results.size() - connected;

        boolean metadataUnavailable =
                results.stream().anyMatch(result -> result.metadata() && !result.connected());

        int nameWidth =
                Math.max(
                        10,
                        results.stream()
                                .mapToInt(result -> result.name().length())
                                .max()
                                .orElse(10));

        int descriptionWidth =
                Math.max(
                        16,
                        results.stream()
                                .mapToInt(result -> result.description().length())
                                .max()
                                .orElse(16));

        String rowFormat = "  %-" + nameWidth + "s %-7s %-" + descriptionWidth + "s %7dms%n";

        StringBuilder report = new StringBuilder();

        report.append("\n").append(HEADER).append("\n\n");

        for (ConnectionStatus result : results) {

            report.append(
                    rowFormat.formatted(
                            result.name(),
                            result.connected() ? "[UP]" : "[DOWN]",
                            result.description(),
                            result.durationMs()));

            if (!result.connected()) {
                appendFailureDetails(report, result);
            }
        }

        report.append("\n")
                .append(
                        "  Total: %d | Connected: %d | Unavailable: %d"
                                .formatted(results.size(), connected, unavailable))
                .append("\n\n")
                .append(SEPARATOR);

        if (metadataUnavailable) {
            log.error("{}", report);
        } else if (unavailable > 0) {
            log.warn("{}", report);
        } else {
            log.info("{}", report);
        }
    }

    private void appendFailureDetails(StringBuilder report, ConnectionStatus result) {
        report.append("    Reason    : ").append(result.reason()).append("\n");

        if (result.sqlState() != null) {
            report.append("    SQLState  : ").append(result.sqlState()).append("\n");
        }

        if (result.errorCode() != null && result.errorCode() != 0) {
            report.append("    ErrorCode : ").append(result.errorCode()).append("\n");
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record ConnectionStatus(
            String name,
            String description,
            boolean metadata,
            boolean connected,
            long durationMs,
            String reason,
            String sqlState,
            Integer errorCode) {}
}
