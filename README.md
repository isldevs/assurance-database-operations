# Assurance Database Operations

## Local (project → environments → data-sources)

Local and production use the same approach: a real Postgres metadata database + Flyway
migrations + `/api/v1/projects` APIs. Locally, Postgres runs in Docker Compose.

1. Start Docker Desktop, then:
   ```powershell
   Copy-Item .env.example .env
   docker compose up -d
   ```
2. Run the API with the local profile (password must match `.env`):
   ```powershell
   $env:POSTGRES_APP_PASSWORD = "change-me-app"
   $env:ORACLE_DB_PASSWORD = "change-me-app"
   .\gradlew.bat bootRun --args="--spring.profiles.active=local"
   ```
3. Demo the hierarchy:
   ```powershell
   # Create project
   Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/v1/projects -ContentType application/json -Body '{"projectCode":"WING_CORE","projectName":"Core Banking Platform","ownerName":"Core Banking Team"}'

   # Map environments, then data sources (use the returned projectId)
   # POST /api/v1/projects/{projectId}/environments
   # POST /api/v1/projects/{projectId}/data-sources
   # GET  /api/v1/projects/{projectId}
   ```

Production uses the same Flyway scripts and APIs; only connection settings change
(`METADATA_JDBC_URL`, `METADATA_DB_USERNAME`, `METADATA_DB_PASSWORD`).

## Gradle maintenance

Use the checked-in Gradle Wrapper (`.\gradlew.bat` on Windows, `./gradlew` on
Linux/macOS) with JDK 21. The wrapper distribution is verified using its SHA-256
checksum. Dependency repositories are centralized in `settings.gradle`.

- `gradle/libs.versions.toml`: plugin versions, library aliases, BOM versions,
  and temporary security overrides. The Spring Boot plugin and BOM share one
  version. Libraries managed by a BOM deliberately have no individual version.
  JUnit follows Boot's BOM to remain compatible with Spring Test; the previous
  JUnit 5 pin caused a missing-method error with Spring Test 7.0.10.
- `build.gradle`: plugins, dependency scopes, native Gradle platforms and
  constraints, and task configuration. Security constraints upgrade transitive
  libraries without introducing them into configurations that do not use them.
- `gradle.lockfile`: resolved dependency versions for reproducible builds.
  Commit it alongside changes to the catalog or dependency declarations.
- `gradle.properties`: enables the configuration cache and local build cache.

For a dependency upgrade:

1. Change the appropriate version in the catalog. For a Boot upgrade, review
   its BOM and remove security overrides only after it supplies fixed versions.
2. Regenerate the locks and review the diff:
   ```powershell
   .\gradlew.bat dependencies --write-locks
   ```
3. Verify the build and rerun your vulnerability scanner:
   ```powershell
   .\gradlew.bat clean check bootJar jacocoTestReport
   ```
   The Spring context test currently needs datasource configuration. Supply the
   test database settings before running the full test suite.

Locks use strict mode: adding dependencies or resolvable configurations requires
an intentional lock update. Locks cover project dependency configurations; plugin
versions are pinned separately in the catalog. To trace a transitive dependency:

```powershell
.\gradlew.bat dependencyInsight --dependency json-smart --configuration testRuntimeClasspath
```

For a Gradle upgrade, run the wrapper task with an approved version and its
official distribution checksum, then commit all generated wrapper files:

```powershell
.\gradlew.bat wrapper --gradle-version <version> --distribution-type bin --gradle-distribution-sha256-sum <sha256>
```

## Release version

Build a production release with the version supplied by your deployment pipeline:

```powershell
.\gradlew.bat bootJar '-PreleaseVersion=1.2.3'
```

Alternatively, set the `APP_VERSION` environment variable before building. The
`releaseVersion` Gradle property takes precedence over `APP_VERSION`. Local builds
default to `0.0.1-SNAPSHOT`.

The build embeds this version as the default in `application.yaml` and uses it
for the application JAR version. The startup banner also displays the Spring Boot
version automatically.

At runtime, `APP_VERSION` overrides the version displayed in the banner. For
Kubernetes, set it in the Deployment's container configuration:

```yaml
spec:
  template:
    spec:
      containers:
        - name: assurance-database-operations
          image: your-registry/assurance-database-operations:1.2.3
          env:
            - name: APP_VERSION
              value: "1.2.3"
```

Updating this environment variable in the Deployment rolls out new pods, which
display the new version on startup. If it is absent, the banner uses the build
version. Set the image tag and `APP_VERSION` from the same pipeline release value
to keep the displayed version consistent with the deployed artifact. The runtime
override changes the displayed version; the JAR's build version stays the same.
