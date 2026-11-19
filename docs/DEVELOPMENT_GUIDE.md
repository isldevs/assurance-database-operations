# Developer Guide

This guide helps developers understand the Database Operations backend, make changes
consistently, and avoid sending a query to the wrong database.

## 1. What this service does

The service is the backend foundation for controlled, non-production database
operations in the Assurance Portal. Its intended lifecycle is:

```text
Project configuration
  -> Activity and script preparation
  -> Validation and approval
  -> Explicit execution request
  -> Asynchronous worker execution
  -> Verification, backup/recovery, audit, and reporting
```

The current implementation is a foundation, not the complete lifecycle. It includes
project/environment metadata APIs, central data-source metadata APIs, target connection
testing, target-pool registration, and JDBC policy. Activity management, the approval
integration, durable queue, separate worker, and backup/recovery workflows are future
work. Do not imply that creating a project or approving future work executes SQL.

## 2. Architecture at a glance

There are two distinct database roles:

| Role | Current configuration | Purpose |
|------|------------------------|---------|
| Metadata database | Spring Boot's primary `spring.datasource` | Project, environment, data-source, and future activity metadata; Flyway migrations |
| Operational target databases | `DataSourceRegistry` and registered Hikari pools | Explicit connections to approved project/environment/database targets |

The default metadata database is PostgreSQL. The target database can be Oracle or
PostgreSQL. They are not interchangeable, and an API's default injected JDBC client
does not automatically route to a target database.

The current target registry is populated from `db-operations.registrations` at startup.
The central data-source metadata API stores definitions, but it does not yet dynamically
register or refresh target pools. Treat that integration as future work.

## 3. Choosing a JDBC API and database

`JdbcClient` and `JdbcTemplate` are Spring JDBC APIs. Neither chooses a database based
on the class or SQL statement; the `DataSource` supplied to the API determines the
database connection.

### Metadata access

Spring Boot auto-configures the primary `DataSource`, `JdbcClient`, and `JdbcTemplate`
from `spring.datasource.*`. In the standard local/test application configuration, this
is the metadata database.

Inject the default `JdbcClient` into metadata repositories:

```java
@Repository
public class ProjectRepository {
    private final JdbcClient jdbcClient;

    public ProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Project> findById(String id) {
        return jdbcClient.sql("""
                        SELECT id, project_code, project_name, owner_name, status,
                               created_at, updated_at
                        FROM db_project
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(projectRowMapper)
                .optional();
    }
}
```

This injected client uses the **metadata datasource**. Use it for metadata repositories
such as projects, environments, data-source definitions, activities, script versions,
approval references, execution history, and audit records.

### Target access

For an already registered operational target, select the key explicitly and use the
`DataSource` returned by the registry:

```java
DataSourceKey key = new DataSourceKey(projectCode, environment, dataSourceCode);
RegisteredDataSource target = dataSourceRegistry.resolve(key);

JdbcTemplate targetJdbc = new JdbcTemplate(target.dataSource());
List<Map<String, Object>> rows =
        targetJdbc.queryForList("SELECT status FROM permitted_table WHERE id = ?", id);
```

The `DataSourceKey` is `(projectCode, environment, dataSourceCode)`. The registry only
resolves registrations that were loaded and approved at startup. An unknown key fails;
do not silently fall back to the metadata database or another target.

To use `JdbcClient` for a selected target, bind a client to the selected datasource:

```java
JdbcClient targetJdbcClient = JdbcClient.create(target.dataSource());
```

Do not inject the default `JdbcClient` or `JdbcTemplate` into target-execution code and
assume it will route based on project context. Do not add a global primary bean per
target. Target selection must be explicit for each operation.

### Connection testing

The connection-test flow loads a project/environment datasource mapping, resolves its
secret reference from Vault KV v2, and passes the mapping engine and URL plus Vault's
username and resolved password to `DataSourceConnectionTester`. The tester creates a target-specific
`DriverManagerDataSource` and `JdbcTemplate` for that one connection test. It does not
use the metadata JDBC connection and does not add the tested datasource to the long-lived
registry.

This short-lived connection-test implementation is separate from the Hikari pool
registry. When dynamic target registration is implemented, connection tests should
continue to be distinguished from managed execution pools.

### Vault secret references

`secretRef` is a relative KV v2 secret path, not a credential and not a mount name.
For example, `projects/WING_CORE/environments/UAT/data-sources/CORE_DB` resolves as
`GET /v1/secret/data/projects/WING_CORE/environments/UAT/data-sources/CORE_DB`; the
secret document must contain `username` and `password` fields. The `secret` KV v2 engine is already
enabled by Vault's local dev server. In Vault UI, open `secret/`, create a secret at
the relative path, add a `username` field for the database login and a `password` field
for its password, and save it.
Use that same relative path in the mapping request's `secretRef`. The API does not
accept or return the target username or password.

Docker Compose exposes local Vault only on loopback at `127.0.0.1:8200`. Its dev server
uses the root token in `VAULT_DEV_ROOT_TOKEN`, stores data only in memory, and must never
be used for shared or production environments. Recreate local secrets after restarting
Vault. For deployed environments, use Vault with TLS and persistent/HA storage, an
application workload identity (not a root token), a narrowly scoped policy granting
read access only to the required KV paths, and an enabled Vault audit device. Inject
Vault address and token through deployment configuration, never source control.

### API selection guide

| Work | Preferred approach | Datasource to bind |
|------|--------------------|--------------------|
| Metadata CRUD and metadata joins | `JdbcClient` | Primary metadata datasource |
| Metadata batch, streaming, or low-level callbacks | `JdbcTemplate` | Primary metadata datasource |
| Target connection test | `JdbcTemplate` / JDBC | Short-lived datasource built from the selected project/environment mapping |
| Target SQL execution | `JdbcTemplate` / JDBC in the worker | Registry-resolved target datasource |
| Script syntax/policy validation | SQL parser and policy service | No target connection unless a controlled check requires one |
| Engine-specific backup/restore | Approved native database tools | Explicit target and backup storage |

`JdbcTemplate` is not a security boundary. Target execution must separately enforce
authorization, approval, script-version immutability, target allowlists, least-privilege
credentials, timeouts, result limits, concurrency limits, and auditing.

## 4. Code organization

Production Java code lives under
`src/main/java/com/company/assurance/dbops/`:

| Package | Responsibility |
|---------|----------------|
| `common/api` | Response envelope, correlation IDs, and exception mapping |
| `datasource/api` | Central data-source API, DTOs, service, and metadata repository |
| `datasource/config` | Target registration and pool configuration |
| `datasource/connection` | Pool creation, connection tests, startup diagnostics, and engine errors |
| `datasource/registry` | Explicit target lookup and pool lifecycle |
| `datasource/validation` | Target approval checks |
| `environment` | Supported environment model |
| `project` | Project hierarchy APIs and metadata |
| `security` | Profile-specific API authorization |

Keep responsibilities separated:

- Controllers handle HTTP mapping and request validation.
- Services enforce business rules and transaction boundaries.
- Repositories contain metadata SQL.
- Connection and execution components operate on explicitly selected targets.
- Request/response DTOs are separate from persistence records.

## 5. Database schema and migrations

Flyway owns the metadata schema in `src/main/resources/db/migration`.

- Add a new versioned migration for schema changes; do not edit an already-applied
  migration such as `V1`.
- Use portable SQL where possible and test against PostgreSQL.
- Add constraints and indexes that express invariants and query patterns.
- Store secret references, not plaintext passwords.
- Do not return JDBC URLs or credentials in API responses.
- Keep target connection definitions separate from project/environment mappings.

The metadata model separates a reusable logical data-source definition from its
project/environment-specific endpoint. The mapping references the logical definition
with a foreign key. See [DATABASE_DESIGN.md](DATABASE_DESIGN.md) before changing these
schema areas; avoid adding further duplicated sources of truth.

## 6. Security and operational rules

- Production/default profiles must remain authenticated. `local` and `test` are explicit
  demo/test profiles and currently allow unauthenticated API access.
- Project and datasource `created_by` are derived from the authenticated Spring Security
  principal. Only `local` and `test` may fall back to the server-configured `anonymous-user`
  (`anonymous` and `test-user`, respectively); never send creator identity in a
  project-create request.
- `db_data_source` stores reusable datasource identity (`code`, `name`, engine, description,
  enabled, and creator); project/environment mappings store endpoint and credential-reference
  details. Create the logical datasource before mapping it to an environment. Connection tests
  target the project/environment mapping, not the logical datasource catalog entry.
- Use the `oauth2` profile for JWT resource-server security where configured.
- Production target connections must be non-production and allowlisted.
- Never log passwords, tokens, raw SQL parameter values, or sensitive customer data.
- Do not expose internal driver exception messages to callers.
- Creating or approving an activity must never trigger execution.
- Long-running SQL belongs in a separate worker and durable queue, not an API request.
- JDBC rollback is not universal recovery. Oracle DDL can implicitly commit.
- Recovery is a separate, explicitly authorized and approved workflow.

## 7. Local development

Use JDK 21 and the checked-in Gradle Wrapper. Follow the root README to configure `.env`
and start Docker Compose. The API's local profile requires metadata and Oracle settings;
do not replace missing values with empty defaults.

Useful commands:

```powershell
.\gradlew.bat clean check
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
.\gradlew.bat integrationTest
.\gradlew.bat dockerIntegrationTest
```

`check` runs formatting, unit/context tests, and the configured JaCoCo coverage gate.
The separate integration tasks require their corresponding database/container
infrastructure.

## 8. Testing expectations

- Unit-test business rules and invalid state transitions.
- Test API success and error responses with MockMvc.
- Test metadata migrations and queries on PostgreSQL for production-relevant SQL.
- Use disposable databases for Oracle/PostgreSQL target integration tests.
- Test that target selection resolves the exact key and never falls back to metadata.
- Test secret resolution and verify secret values do not appear in responses or logs.
- Test the approval/execute boundary: no SQL runs before explicit authorization.
- Cover partial failure, cancellation, retry, and recovery once execution workflows exist.

## 9. Adding a feature

For each feature:

1. Define the domain behavior and authorization rules.
2. Add a Flyway migration when persistence changes.
3. Implement domain/service logic and focused unit tests.
4. Add repository SQL with parameter binding for metadata operations.
5. Add request/response DTOs and controller routes.
6. Add audit events for meaningful operational state changes.
7. Add integration tests for persistence and API behavior.
8. Update this guide and the relevant technology decision.

Before using a JDBC client in new code, answer two questions in the code review:

1. Is this a metadata operation or a target database operation?
2. Which exact `DataSource` is bound to the `JdbcClient` or `JdbcTemplate`?

If the target cannot be identified explicitly, do not implement execution against it yet.
