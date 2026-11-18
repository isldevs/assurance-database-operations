# Technology Decisions – Assurance Database Operations

**Audience:** Backend / platform team  
**Purpose:** Explain which technologies this project uses, why they were chosen, and how they compare to common alternatives.  
**Scope:** Non-production Database Operations module (UAT / STAGE / STAGE2 / STAGE3).

---

## 1. Summary (one-page view)

| Area | Chosen technology | Why (short) |
|------|-------------------|-------------|
| Language / runtime | **Java 21** | LTS, records, pattern matching, strong enterprise support |
| Framework | **Spring Boot 4.1** | Standard for portal APIs, security, Actuator, JDBC |
| Build | **Gradle + version catalog + lockfile** | Reproducible, secure dependency management |
| Metadata persistence | **PostgreSQL + Flyway + JdbcClient** | Schema-first, explicit SQL, low overhead |
| Target DB access | **HikariCP + custom DataSourceRegistry** | One project → many envs → many datasources |
| Local infra | **Docker Compose (Postgres 17)** | Same engine family as production |
| Security | **Spring Security** (+ OAuth2 profile later) | Portal SSO / Azure AD ready |
| Observability | **Actuator + Micrometer + OpenTelemetry** | Health, metrics, tracing |
| Tests | **JUnit 5 + MockMvc + H2 (test) / Docker Postgres (local)** | Fast CI + production-like local |

---

## 2. Java 21

### Why
- Long-term support (LTS) suitable for bank / assurance platforms.
- Modern language features used in this codebase: `record`, `switch` expressions, stronger null-handling patterns.
- Works well with Spring Boot 4 and current JDBC drivers (Oracle / PostgreSQL).

### Compared to alternatives

| Option | Pros | Cons | Fit for this project? |
|--------|------|------|------------------------|
| **Java 21** | LTS, mature ecosystem, Spring Boot 4 support | — | **Yes – chosen** |
| Java 17 | Also LTS | Older; fewer language improvements | Acceptable, but 21 is preferred |
| Kotlin | Concise syntax | Extra skill/tooling cost for the whole team | Not needed for this module |
| Go / Node | Fast for some I/O services | Different ops/security model; weak fit with existing Spring portal | No |

### Example
```java
public record DataSourceKey(String projectCode, Environment environment, String dataSourceCode) {
    // Immutable value object – ideal for (project, env, datasource) identity
}
```

---

## 3. Spring Boot 4

### Why
- Matches Assurance Portal backend standards.
- Gives us HTTP APIs, validation, security, Actuator, JDBC, Flyway integration out of the box.
- Clear profile model: `local`, `test`, future `oauth2` / production.

### Compared to alternatives

| Option | Pros | Cons | Fit? |
|--------|------|------|------|
| **Spring Boot** | Ecosystem, security, ops, team familiarity | Heavier than a tiny HTTP server | **Yes** |
| Quarkus / Micronaut | Fast startup, low memory | Team rewrite; different patterns | No for portal alignment |
| Plain Jakarta EE | Standards-based | More boilerplate, slower delivery | No |
| Node/NestJS | Fast iteration | Not aligned with current Java portal stack | No |

### Example – API style used here
```http
POST /api/v1/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/environments
POST /api/v1/projects/{projectId}/data-sources
```

Standard response envelope (from the module design doc):

```json
{
  "success": true,
  "data": { "projectId": "...", "projectCode": "WING_CORE" },
  "correlationId": "corr-123"
}
```

---

## 4. Build: Gradle + Version Catalog + Dependency Locking

### Why
- **Version catalog** (`gradle/libs.versions.toml`): one place for versions and library aliases.
- **Strict lockfile**: reproducible builds; intentional upgrades only.
- **Security constraints**: patch CVEs in transitive libs without waiting for a full Boot upgrade.
- Wrapper + checksum: every developer/CI uses the same Gradle.

### Compared to alternatives

| Option | Pros | Cons | Fit? |
|--------|------|------|------|
| **Gradle + catalog + locks** | Fast, flexible, reproducible | Learning curve vs Maven | **Yes** |
| Maven + BOM | Familiar to many Java teams | Weaker locking ergonomics | Possible, not current choice |
| Unlocked Gradle | Easy | “Works on my machine” / surprise CVE versions | No |

### Example – upgrade process
1. Change version in `libs.versions.toml`
2. `.\gradlew.bat dependencies --write-locks`
3. `.\gradlew.bat clean check bootJar`

---

## 5. Metadata database access: why JdbcClient (not JPA)

This is the decision most often questioned. For **project / environment / datasource configuration**, we use:

**PostgreSQL + Flyway migrations + Spring `JdbcClient`**

### What JdbcClient is
- Modern Spring JDBC API (Spring Framework 6.1+).
- Fluent wrapper over JDBC (same family as `JdbcTemplate`).
- You write SQL; Spring binds parameters and maps rows.
- **Not** legacy technology — it is the current recommended JDBC style in Spring.

### Why it fits this module
1. Schema is owned by **Flyway** (source of truth), not by ORM entity mappings.
2. Queries are simple and must stay auditable / reviewable for a controlled DB-ops platform.
3. Lower runtime overhead than JPA for small metadata tables.
4. Exact SQL joins match the document model: Project → Environment → DataSource.
5. Credentials / JDBC URLs stay under our control (never accidentally serialized by entity graphs).

### Comparison with examples

#### A) JdbcClient (chosen)

```java
jdbcClient.sql("""
        SELECT id, project_code, project_name, owner_name, status, created_at, updated_at
        FROM db_project
        WHERE id = :id
        """)
    .param("id", projectId)
    .query((rs, rowNum) -> new Project(...))
    .optional();
```

| Pros | Cons |
|------|------|
| Explicit SQL, easy to review | More SQL written by hand |
| Predictable performance | Less “magic” for complex graphs |
| Maps 1:1 to Flyway tables | No automatic dirty checking |
| Excellent for ops / audit-heavy systems | |

#### B) Spring Data JPA / Hibernate

```java
@Entity
class ProjectEntity {
    @OneToMany(mappedBy = "project")
    List<ProjectEnvironmentEntity> environments;
}

projectRepository.findById(id); // may trigger lazy loads / joins
```

| Pros | Cons |
|------|------|
| Fast feature coding for rich domains | Hidden SQL, N+1 risk |
| Relationship navigation | Extra memory / flush / session cost |
| Large ecosystem | Easy to leak fields (e.g. jdbcUrl) in JSON if not careful |

**When JPA would be better:** large CRUD admin domains with many relations and little custom SQL.  
**For this Phase 1 metadata:** JdbcClient is the better default.

#### C) jOOQ

```java
dsl.selectFrom(DB_PROJECT)
   .where(DB_PROJECT.ID.eq(projectId))
   .fetchOptionalInto(Project.class);
```

| Pros | Cons |
|------|------|
| Type-safe SQL, still JDBC-fast | Extra codegen / build complexity |
| Great for complex reporting SQL | Another tool for the team to learn |

**Possible future upgrade** if reporting queries become complex — without giving up JDBC performance.

#### D) R2DBC (reactive)

| Pros | Cons |
|------|------|
| Non-blocking DB I/O | Needs fully reactive stack |
| | Poor fit with current Tomcat + JDBC workers + Oracle driver reality |
| | Harder transaction / team model |

**Not chosen:** this service is request/thread + worker/queue oriented, not a reactive pipeline.

### Performance reality check

For metadata tables (`db_project`, mappings), **JdbcClient and jOOQ are typically as fast or faster than JPA**.

Real performance risk in Database Operations is **not** the ORM choice — it is:

- target database pool sizing (Hikari)
- concurrent executions across environments
- worker throughput and queue design
- backup/restore I/O

That is why the project already models `maximum-total-connections` and per-pool limits.

---

## 6. Two datasource roles (important architecture)

This project intentionally uses **two different database roles**:

| Role | Technology | Purpose |
|------|------------|---------|
| **Metadata DB** | Spring `spring.datasource` + Flyway + JdbcClient | Store projects, envs, datasource mappings, later activities |
| **Target DBs** | Custom `DataSourceRegistry` + Hikari pools | Connect to UAT/STAGE Oracle/Postgres operational databases |

### Why not one Spring Boot datasource only?
Spring Boot’s default model is **one primary datasource**.  
Our business model is:

```text
Project WING_CORE
 ├─ UAT
 │   ├─ CORE_DB
 │   └─ PERSONA_DB
 └─ STAGE
     ├─ CORE_DB
     └─ REPORTING_DB
```

So we need **many target pools**, keyed by `(projectCode, environment, dataSourceCode)`.

### Example key
```text
WING_CORE + UAT   + CORE_DB  → pool A
WING_CORE + STAGE + CORE_DB  → pool B   (same code, different environment/target)
```

### Compared to alternatives

| Approach | Pros | Cons | Fit? |
|----------|------|------|------|
| **Custom registry + Hikari** | Exact control, budgets, approval checks | More code than Boot auto-config | **Yes** |
| AbstractRoutingDataSource | Built-in routing | Awkward for many dynamic targets + lifecycle | Partial |
| New connection per request | Simple | Terrible performance / leaks | No |
| Single shared DB user for all targets | Easy | Breaks isolation and least privilege | No |

---

## 7. HikariCP

### Why
- De-facto high-performance JDBC pool in Spring Boot.
- Fast get/return of connections; low overhead.
- Supports metrics (Micrometer) used in this project.

### Example settings used in target pools
- Small `maximumPoolSize` per target (e.g. 3)
- Global budget via `db-operations.maximum-total-connections`
- Read-only advisory flag for controlled operations
- Driver timeouts for Oracle / PostgreSQL

### Compared to alternatives

| Pool | Notes |
|------|------|
| **HikariCP** | Best default performance / Spring integration |
| Tomcat / DBCP / C3P0 | Older / heavier; no reason to prefer |

---

## 8. Flyway

### Why
- Schema changes are versioned SQL files in Git.
- Same scripts run in **local Docker Postgres** and **production metadata DB**.
- Reviewable by DBAs; matches controlled-change culture.

### Example
```text
src/main/resources/db/migration/V1__project_hierarchy.sql
```

Creates:

- `db_environment`
- `db_project`
- `db_project_environment`
- `db_project_env_datasource`

### Compared to alternatives

| Option | Pros | Cons | Fit? |
|--------|------|------|------|
| **Flyway** | Simple versioned SQL | — | **Yes** |
| Liquibase | Powerful XML/YAML/SQL | Heavier for current needs | Optional later |
| Hibernate `ddl-auto` | Fast demos | Unsafe for shared/prod metadata | **No** |
| Manual DBA scripts only | Full control | Drift between envs | Avoid as primary process |

---

## 9. PostgreSQL (metadata) + Oracle/PostgreSQL (targets)

### Metadata DB: PostgreSQL
- Excellent local Docker support.
- Strong fit for application metadata.
- Production can use managed Postgres with the **same Flyway scripts**.

> Design doc mentions Oracle metadata in the broader portal. For this service we standardize on Postgres metadata for local/prod parity via Docker/managed Postgres. Target databases remain Oracle and/or PostgreSQL as registered.

### Target DBs
- Drivers on classpath: Oracle JDBC + PostgreSQL.
- Engine validated against JDBC URL prefix.
- Non-prod environments only: `UAT`, `STAGE`, `STAGE2`, `STAGE3`.

### Compared to alternatives for metadata

| Option | Local DX | Prod maturity | Fit? |
|--------|----------|---------------|--------------|
| **PostgreSQL** | Excellent with Docker | Excellent | **Yes** |
| Oracle XE in Docker | Heavier, licensing/friction | Matches some enterprise standards | Possible later |
| H2 everywhere | Fast tests | Not production-like | **Tests only** |

---

## 10. Docker Compose (local = production pattern)

### Why
- Developers run the **same database engine** as production metadata.
- App config differs only by connection env vars:
  - `METADATA_JDBC_URL`
  - `METADATA_DB_USERNAME`
  - `METADATA_DB_PASSWORD`
- Avoids “works with H2, fails in prod” for schema features.

### Example local flow
```powershell
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### Compared to alternatives

| Option | Pros | Cons |
|--------|------|------|
| **Docker Compose Postgres** | Prod-like, shareable | Requires Docker Desktop |
| In-memory only | Zero setup | Weak demo for lead/DBA audience |
| Shared remote DEV DB | Real data | Coupling, conflicts, harder cleanup |

---

## 11. Security stack

### Current
- `spring-boot-starter-security`
- Open API access on non-`oauth2` profiles (local / test)
- `oauth2` profile reserved for JWT resource-server mode (Azure AD later)

### Why this staged approach
- hierarchy + APIs, not full IAM integration yet.
- Production path is already prepared (OAuth2 resource server dependency present).

### Compared to alternatives

| Option | Notes |
|--------|------|
| **Spring Security + OAuth2 RS** | Portal standard |
| Custom token filters only | Easy to get wrong |
| No security until later | Risky habit; we keep the hooks ready |

### Additional safety choices already in code
- Target approval allow-list before pool creation
- API responses omit JDBC URLs / passwords (`secretRef` only)
- Safe `toString()` on registration properties

---

## 12. Observability: Actuator, Micrometer, OpenTelemetry

### Why
- Operational module must be measurable: health, pool metrics, traces.
- Separate management port (`8081`) keeps ops endpoints off the main API port.

### Compared to alternatives

| Option | Fit |
|--------|-----|
| **Actuator + Micrometer + OTLP** | Yes – Spring standard |
| Manual logging only | Insufficient for production ops |
| Vendor-only agents | Possible later, not a replacement for metrics design |

---

## 13. Testing strategy

| Layer | Tool | Why |
|-------|------|-----|
| Unit | JUnit 5 + Mockito | Fast service rules (normalization, mapping order) |
| API integration | Spring MockMvc + H2 (PostgreSQL mode) | Proves one project / multi-env / multi-datasource without Docker |
| Local demo | Docker Postgres | Same Flyway path as production |

### Why not Testcontainers for every test (yet)?
- H2 keeps CI fast.
- Docker Compose covers the production-like path for demos.
- Testcontainers is a good next step when the team wants CI = exact Postgres engine.

---

## 14. Decision guide for the team (“when do we change?”)

| If the need is… | Keep / choose |
|-----------------|---------------|
| Simple metadata CRUD aligned to Flyway | **JdbcClient** |
| Very complex analytical SQL | Consider **jOOQ** |
| Large entity-heavy admin domain | Consider **JPA** for that bounded context only |
| Many target DBs per project/env | Keep **DataSourceRegistry + Hikari** |
| Blocking SQL workers + Tomcat API | Keep **JDBC** (not R2DBC) |
| Local/prod schema parity | Keep **Flyway + Postgres** |

---

## 15. How to explain this in a 2-minute team update

1. **We are building a controlled multi-target DB ops API**, not a normal single-database CRUD app.  
2. **Metadata** (projects/envs/sources) uses Postgres + Flyway + JdbcClient for explicit, fast, reviewable SQL.  
3. **Target databases** use a custom Hikari registry so one project can own many environments and many datasources safely.  
4. **Local Docker Postgres uses the same migration/API path as production** — only connection settings change.  
5. **JPA was not chosen for performance theater** — for this layer, JDBC is simpler and typically faster; the real scale challenges are pools, workers, and execution orchestration.

---

## 16. Related documents

- Module design: `Non Prod Database Recovery Module Doc.pdf`
- Local run steps: root `README.md`
- Dependency upgrade process: root `README.md` (Gradle maintenance section)
