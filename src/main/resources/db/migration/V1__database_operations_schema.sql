-- Fresh-install baseline for the Database Operations METADATA database.
-- Do not run this script against an operational target database.

CREATE TABLE db_project
(
    id             VARCHAR(36)  NOT NULL,
    project_code   VARCHAR(64)  NOT NULL,
    project_name   VARCHAR(128) NOT NULL,
    owner_name     VARCHAR(128) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    promotion_mode VARCHAR(16)  NOT NULL DEFAULT 'SEQUENTIAL',
    created_by     VARCHAR(128) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_project PRIMARY KEY (id),
    CONSTRAINT uq_db_project_code UNIQUE (project_code),
    CONSTRAINT ck_db_project_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_db_project_promotion CHECK (promotion_mode IN ('SEQUENTIAL', 'INDEPENDENT')),
    CONSTRAINT ck_db_project_row_version CHECK (row_version >= 0)
);

CREATE TABLE db_project_member
(
    id         VARCHAR(36)  NOT NULL,
    project_id VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(128) NOT NULL,
    role_code  VARCHAR(32)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_project_member PRIMARY KEY (id),
    CONSTRAINT uq_db_project_member_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_db_project_member_project
        FOREIGN KEY (project_id) REFERENCES db_project (id),
    CONSTRAINT ck_db_project_member_role
        CHECK (role_code IN ('PROJECT_ADMIN', 'DBA', 'OPERATOR', 'VIEWER'))
);

CREATE TABLE db_environment
(
    code          VARCHAR(16)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    description   VARCHAR(128) NOT NULL,
    sort_order    SMALLINT     NOT NULL,
    is_production BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_db_environment PRIMARY KEY (code),
    CONSTRAINT uq_db_environment_sort_order UNIQUE (sort_order),
    CONSTRAINT ck_db_environment_sort_order CHECK (sort_order > 0),
    CONSTRAINT ck_db_environment_nonproduction
        CHECK (code <> 'PROD' OR is_production = TRUE)
);

INSERT INTO db_environment (code, name, description, sort_order, is_production, enabled)
VALUES ('UAT', 'UAT', 'User acceptance testing', 1, FALSE, TRUE),
       ('STAGE', 'Stage', 'Staging environment', 2, FALSE, TRUE),
       ('STAGE2', 'Stage2', 'Secondary staging environment', 3, FALSE, TRUE),
       ('STAGE3', 'Stage3', 'Additional staging environment', 4, FALSE, TRUE);

CREATE TABLE db_project_environment
(
    id                  VARCHAR(36) NOT NULL,
    project_id          VARCHAR(36) NOT NULL,
    environment_code    VARCHAR(16) NOT NULL,
    enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
    promotion_order     SMALLINT,
    approval_policy_ref VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version         INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_project_environment PRIMARY KEY (id),
    CONSTRAINT uq_db_project_environment UNIQUE (project_id, environment_code),
    CONSTRAINT uq_db_project_environment_id_project UNIQUE (id, project_id),
    CONSTRAINT uq_db_project_environment_order UNIQUE (project_id, promotion_order),
    CONSTRAINT fk_db_project_environment_project
        FOREIGN KEY (project_id) REFERENCES db_project (id),
    CONSTRAINT fk_db_project_environment_env
        FOREIGN KEY (environment_code) REFERENCES db_environment (code),
    CONSTRAINT ck_db_project_environment_order
        CHECK (promotion_order IS NULL OR promotion_order > 0),
    CONSTRAINT ck_db_project_environment_version CHECK (row_version >= 0)
);

CREATE TABLE db_data_source
(
    id               VARCHAR(36)  NOT NULL,
    data_source_code VARCHAR(64)  NOT NULL,
    data_source_name VARCHAR(128) NOT NULL,
    engine           VARCHAR(16)  NOT NULL,
    description      VARCHAR(500),
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_data_source PRIMARY KEY (id),
    CONSTRAINT uq_db_data_source_code UNIQUE (data_source_code),
    CONSTRAINT uq_db_data_source_id_engine UNIQUE (id, engine),
    CONSTRAINT ck_db_data_source_engine CHECK (engine IN ('ORACLE', 'POSTGRESQL')),
    CONSTRAINT ck_db_data_source_version CHECK (row_version >= 0)
);

CREATE TABLE db_project_env_datasource
(
    id                     VARCHAR(36)  NOT NULL,
    project_environment_id VARCHAR(36)  NOT NULL,
    data_source_id         VARCHAR(36)  NOT NULL,
    schema_name            VARCHAR(128),
    jdbc_url               VARCHAR(512) NOT NULL,
    username               VARCHAR(128) NOT NULL,
    secret_ref             VARCHAR(256) NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    connection_timeout_ms  INTEGER      NOT NULL DEFAULT 3000,
    validation_timeout_ms  INTEGER      NOT NULL DEFAULT 3000,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_project_env_datasource PRIMARY KEY (id),
    CONSTRAINT uq_db_project_env_datasource
        UNIQUE (project_environment_id, data_source_id),
    CONSTRAINT uq_db_project_env_datasource_id_env
        UNIQUE (id, project_environment_id),
    CONSTRAINT fk_db_project_env_datasource_env
        FOREIGN KEY (project_environment_id) REFERENCES db_project_environment (id),
    CONSTRAINT fk_db_project_env_datasource_source
        FOREIGN KEY (data_source_id) REFERENCES db_data_source (id),
    CONSTRAINT ck_db_project_env_datasource_timeouts
        CHECK (connection_timeout_ms >= 250 AND validation_timeout_ms >= 250),
    CONSTRAINT ck_db_project_env_datasource_version CHECK (row_version >= 0)
);

CREATE TABLE db_activity_type
(
    id          VARCHAR(36)  NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    risk_class  VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_activity_type PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_type_code UNIQUE (code),
    CONSTRAINT uq_db_activity_type_id_code UNIQUE (id, code),
    CONSTRAINT ck_db_activity_type_risk
        CHECK (risk_class IN ('LOW', 'STANDARD', 'HIGH', 'CRITICAL'))
);

CREATE TABLE db_sub_activity_type
(
    id               VARCHAR(36)  NOT NULL,
    activity_type_id VARCHAR(36)  NOT NULL,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(500),
    risk_class       VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_sub_activity_type PRIMARY KEY (id),
    CONSTRAINT uq_db_sub_activity_type_code UNIQUE (activity_type_id, code),
    CONSTRAINT uq_db_sub_activity_type_parent UNIQUE (id, activity_type_id),
    CONSTRAINT fk_db_sub_activity_type_parent
        FOREIGN KEY (activity_type_id) REFERENCES db_activity_type (id),
    CONSTRAINT ck_db_sub_activity_type_risk
        CHECK (risk_class IN ('LOW', 'STANDARD', 'HIGH', 'CRITICAL'))
);

CREATE TABLE db_activity_template
(
    id                   VARCHAR(36)  NOT NULL,
    code                 VARCHAR(64)  NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    description          VARCHAR(500),
    activity_type_id     VARCHAR(36)  NOT NULL,
    sub_activity_type_id VARCHAR(36),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by           VARCHAR(128) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version          INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_activity_template PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_template_code UNIQUE (code),
    CONSTRAINT fk_db_activity_template_type
        FOREIGN KEY (activity_type_id) REFERENCES db_activity_type (id),
    CONSTRAINT fk_db_activity_template_subtype
        FOREIGN KEY (sub_activity_type_id, activity_type_id)
            REFERENCES db_sub_activity_type (id, activity_type_id),
    CONSTRAINT ck_db_activity_template_version CHECK (row_version >= 0)
);

CREATE TABLE db_activity_template_version
(
    id                     VARCHAR(36)  NOT NULL,
    template_id            VARCHAR(36)  NOT NULL,
    version_number         INTEGER      NOT NULL,
    configuration_document TEXT         NOT NULL,
    content_sha256         CHAR(64)     NOT NULL,
    created_by             VARCHAR(128) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_activity_template_version PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_template_version UNIQUE (template_id, version_number),
    CONSTRAINT fk_db_activity_template_version_template
        FOREIGN KEY (template_id) REFERENCES db_activity_template (id),
    CONSTRAINT ck_db_activity_template_version_number CHECK (version_number > 0),
    CONSTRAINT ck_db_activity_template_sha CHECK (content_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_activity
(
    id                   VARCHAR(36)  NOT NULL,
    activity_code        VARCHAR(32)  NOT NULL,
    project_id           VARCHAR(36)  NOT NULL,
    activity_type_id     VARCHAR(36)  NOT NULL,
    sub_activity_type_id VARCHAR(36),
    template_version_id  VARCHAR(36),
    activity_name        VARCHAR(128) NOT NULL,
    change_reference     VARCHAR(128) NOT NULL,
    description          VARCHAR(2000),
    priority             VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status               VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    execution_mode       VARCHAR(16)  NOT NULL DEFAULT 'SEQUENTIAL',
    requested_by         VARCHAR(128) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version          INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_db_activity PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_code UNIQUE (activity_code),
    CONSTRAINT uq_db_activity_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_db_activity_project FOREIGN KEY (project_id) REFERENCES db_project (id),
    CONSTRAINT fk_db_activity_type FOREIGN KEY (activity_type_id) REFERENCES db_activity_type (id),
    CONSTRAINT fk_db_activity_subtype
        FOREIGN KEY (sub_activity_type_id, activity_type_id)
            REFERENCES db_sub_activity_type (id, activity_type_id),
    CONSTRAINT fk_db_activity_template_version
        FOREIGN KEY (template_version_id) REFERENCES db_activity_template_version (id),
    CONSTRAINT ck_db_activity_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_db_activity_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
                          'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_db_activity_execution_mode CHECK (execution_mode IN ('SEQUENTIAL', 'INDEPENDENT')),
    CONSTRAINT ck_db_activity_version CHECK (row_version >= 0)
);

CREATE TABLE db_activity_env
(
    id                     VARCHAR(36) NOT NULL,
    activity_id            VARCHAR(36) NOT NULL,
    project_id             VARCHAR(36) NOT NULL,
    project_environment_id VARCHAR(36) NOT NULL,
    execution_order        SMALLINT    NOT NULL,
    status                 VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approval_policy_ref    VARCHAR(128),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_activity_env PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_env UNIQUE (activity_id, project_environment_id),
    CONSTRAINT uq_db_activity_env_order UNIQUE (activity_id, execution_order),
    CONSTRAINT uq_db_activity_env_id_environment UNIQUE (id, project_environment_id),
    CONSTRAINT fk_db_activity_env_activity_project
        FOREIGN KEY (activity_id, project_id) REFERENCES db_activity (id, project_id),
    CONSTRAINT fk_db_activity_env_project_environment
        FOREIGN KEY (project_environment_id, project_id)
            REFERENCES db_project_environment (id, project_id),
    CONSTRAINT ck_db_activity_env_order CHECK (execution_order > 0),
    CONSTRAINT ck_db_activity_env_status
        CHECK (status IN ('PENDING', 'READY', 'BLOCKED', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE TABLE db_activity_env_source
(
    id                        VARCHAR(36) NOT NULL,
    activity_env_id           VARCHAR(36) NOT NULL,
    project_environment_id    VARCHAR(36) NOT NULL,
    project_env_datasource_id VARCHAR(36) NOT NULL,
    execution_order           SMALLINT    NOT NULL,
    required                  BOOLEAN     NOT NULL DEFAULT TRUE,
    status                    VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_activity_env_source PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_env_source UNIQUE (activity_env_id, project_env_datasource_id),
    CONSTRAINT uq_db_activity_env_source_id UNIQUE (id, activity_env_id),
    CONSTRAINT fk_db_activity_env_source_activity_env
        FOREIGN KEY (activity_env_id, project_environment_id)
            REFERENCES db_activity_env (id, project_environment_id),
    CONSTRAINT fk_db_activity_env_source_mapping
        FOREIGN KEY (project_env_datasource_id, project_environment_id)
            REFERENCES db_project_env_datasource (id, project_environment_id),
    CONSTRAINT ck_db_activity_env_source_order CHECK (execution_order > 0),
    CONSTRAINT ck_db_activity_env_source_status
        CHECK (status IN ('PENDING', 'PRECHECK_RUNNING', 'PRECHECK_FAILED', 'BACKUP_RUNNING',
                          'BACKUP_COMPLETED', 'EXECUTING', 'VERIFYING', 'COMPLETED', 'FAILED',
                          'CANCEL_REQUESTED', 'CANCELLED'))
);

CREATE TABLE db_activity_env_source_dependency
(
    source_id            VARCHAR(36) NOT NULL,
    depends_on_source_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_db_activity_env_source_dependency PRIMARY KEY (source_id, depends_on_source_id),
    CONSTRAINT fk_db_activity_source_dependency_source
        FOREIGN KEY (source_id) REFERENCES db_activity_env_source (id),
    CONSTRAINT fk_db_activity_source_dependency_parent
        FOREIGN KEY (depends_on_source_id) REFERENCES db_activity_env_source (id),
    CONSTRAINT ck_db_activity_source_no_self_dependency CHECK (source_id <> depends_on_source_id)
);

CREATE TABLE db_script_package
(
    id           VARCHAR(36)  NOT NULL,
    package_code VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(500),
    owner_id     VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_script_package PRIMARY KEY (id),
    CONSTRAINT uq_db_script_package_code UNIQUE (package_code)
);

CREATE TABLE db_script_package_version
(
    id             VARCHAR(36)  NOT NULL,
    package_id     VARCHAR(36)  NOT NULL,
    version_number VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    content_sha256 CHAR(64),
    created_by     VARCHAR(128) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at   TIMESTAMPTZ,
    CONSTRAINT pk_db_script_package_version PRIMARY KEY (id),
    CONSTRAINT uq_db_script_package_version UNIQUE (package_id, version_number),
    CONSTRAINT uq_db_script_package_version_id UNIQUE (id, package_id),
    CONSTRAINT fk_db_script_package_version_package
        FOREIGN KEY (package_id) REFERENCES db_script_package (id),
    CONSTRAINT ck_db_script_package_version_status CHECK (status IN ('DRAFT', 'VALIDATED', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_db_script_package_version_sha
        CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_script
(
    id                 VARCHAR(36)  NOT NULL,
    package_version_id VARCHAR(36)  NOT NULL,
    script_code        VARCHAR(64)  NOT NULL,
    script_name        VARCHAR(128) NOT NULL,
    script_type        VARCHAR(16)  NOT NULL,
    sql_content        TEXT         NOT NULL,
    content_sha256     CHAR(64)     NOT NULL,
    execution_order    SMALLINT     NOT NULL,
    transaction_mode   VARCHAR(16)  NOT NULL DEFAULT 'AUTO_COMMIT',
    timeout_seconds    INTEGER      NOT NULL DEFAULT 300,
    error_policy       VARCHAR(16)  NOT NULL DEFAULT 'STOP',
    description        VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_script PRIMARY KEY (id),
    CONSTRAINT uq_db_script_code UNIQUE (package_version_id, script_code),
    CONSTRAINT uq_db_script_id_package UNIQUE (id, package_version_id),
    CONSTRAINT fk_db_script_package_version
        FOREIGN KEY (package_version_id) REFERENCES db_script_package_version (id),
    CONSTRAINT ck_db_script_type CHECK (script_type IN
                                        ('BACKUP', 'EXECUTION', 'VERIFICATION', 'ROLLBACK', 'COMPARISON')),
    CONSTRAINT ck_db_script_sha CHECK (content_sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_db_script_order CHECK (execution_order > 0),
    CONSTRAINT ck_db_script_timeout CHECK (timeout_seconds BETWEEN 1 AND 86400),
    CONSTRAINT ck_db_script_transaction CHECK (transaction_mode IN ('AUTO_COMMIT', 'MANUAL')),
    CONSTRAINT ck_db_script_error_policy CHECK (error_policy IN ('STOP', 'CONTINUE'))
);

CREATE TABLE db_script_parameter
(
    id             VARCHAR(36)  NOT NULL,
    script_id      VARCHAR(36)  NOT NULL,
    parameter_name VARCHAR(128) NOT NULL,
    data_type      VARCHAR(16)  NOT NULL,
    required       BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value  VARCHAR(1000),
    sensitive      BOOLEAN      NOT NULL DEFAULT FALSE,
    description    VARCHAR(500),
    CONSTRAINT pk_db_script_parameter PRIMARY KEY (id),
    CONSTRAINT uq_db_script_parameter UNIQUE (script_id, parameter_name),
    CONSTRAINT fk_db_script_parameter_script FOREIGN KEY (script_id) REFERENCES db_script (id),
    CONSTRAINT ck_db_script_parameter_type
        CHECK (data_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP'))
);

CREATE TABLE db_script_dependency
(
    script_id            VARCHAR(36) NOT NULL,
    depends_on_script_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_db_script_dependency PRIMARY KEY (script_id, depends_on_script_id),
    CONSTRAINT fk_db_script_dependency_script FOREIGN KEY (script_id) REFERENCES db_script (id),
    CONSTRAINT fk_db_script_dependency_parent
        FOREIGN KEY (depends_on_script_id) REFERENCES db_script (id),
    CONSTRAINT ck_db_script_no_self_dependency CHECK (script_id <> depends_on_script_id)
);

CREATE TABLE db_activity_script_package_assignment
(
    id                     VARCHAR(36)  NOT NULL,
    activity_env_source_id VARCHAR(36)  NOT NULL,
    package_version_id     VARCHAR(36)  NOT NULL,
    assignment_mode        VARCHAR(16)  NOT NULL DEFAULT 'PER_SOURCE',
    assigned_by            VARCHAR(128) NOT NULL,
    assigned_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_db_activity_script_package_assignment PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_script_package_assignment UNIQUE
        (activity_env_source_id, package_version_id),
    CONSTRAINT fk_db_activity_script_assignment_source
        FOREIGN KEY (activity_env_source_id) REFERENCES db_activity_env_source (id),
    CONSTRAINT fk_db_activity_script_assignment_version
        FOREIGN KEY (package_version_id) REFERENCES db_script_package_version (id),
    CONSTRAINT ck_db_activity_script_assignment_mode
        CHECK (assignment_mode IN ('PER_SOURCE', 'SHARED', 'TEMPLATE'))
);

CREATE TABLE db_activity_approval
(
    id                       VARCHAR(36)  NOT NULL,
    activity_id              VARCHAR(36)  NOT NULL,
    approval_request_id      VARCHAR(128) NOT NULL,
    approval_status          VARCHAR(16)  NOT NULL,
    requested_by             VARCHAR(128) NOT NULL,
    submitted_at             TIMESTAMPTZ  NOT NULL,
    decided_at               TIMESTAMPTZ,
    approved_snapshot_sha256 CHAR(64)     NOT NULL,
    correlation_id           VARCHAR(128),
    CONSTRAINT pk_db_activity_approval PRIMARY KEY (id),
    CONSTRAINT uq_db_activity_approval_request UNIQUE (approval_request_id),
    CONSTRAINT fk_db_activity_approval_activity FOREIGN KEY (activity_id) REFERENCES db_activity (id),
    CONSTRAINT ck_db_activity_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_db_activity_approval_sha CHECK (approved_snapshot_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_execution
(
    id                       VARCHAR(36)  NOT NULL,
    activity_id              VARCHAR(36)  NOT NULL,
    attempt_number           INTEGER      NOT NULL DEFAULT 1,
    idempotency_key          VARCHAR(128) NOT NULL,
    requested_by             VARCHAR(128) NOT NULL,
    status                   VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    execution_mode           VARCHAR(16)  NOT NULL,
    approved_snapshot_sha256 CHAR(64)     NOT NULL,
    requested_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at               TIMESTAMPTZ,
    ended_at                 TIMESTAMPTZ,
    correlation_id           VARCHAR(128),
    CONSTRAINT pk_db_execution PRIMARY KEY (id),
    CONSTRAINT uq_db_execution_idempotency UNIQUE (requested_by, idempotency_key),
    CONSTRAINT uq_db_execution_activity_attempt UNIQUE (activity_id, attempt_number),
    CONSTRAINT fk_db_execution_activity FOREIGN KEY (activity_id) REFERENCES db_activity (id),
    CONSTRAINT ck_db_execution_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_db_execution_status CHECK (status IN ('PENDING', 'PRECHECK_RUNNING', 'READY', 'RUNNING', 'COMPLETED',
                                                        'PARTIALLY_COMPLETED', 'FAILED', 'CANCEL_REQUESTED',
                                                        'CANCELLED')),
    CONSTRAINT ck_db_execution_mode CHECK (execution_mode IN ('SEQUENTIAL', 'INDEPENDENT')),
    CONSTRAINT ck_db_execution_sha CHECK (approved_snapshot_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_environment_execution
(
    id              VARCHAR(36) NOT NULL,
    execution_id    VARCHAR(36) NOT NULL,
    activity_env_id VARCHAR(36) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    execution_order SMALLINT    NOT NULL,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    CONSTRAINT pk_db_environment_execution PRIMARY KEY (id),
    CONSTRAINT uq_db_environment_execution UNIQUE (execution_id, activity_env_id),
    CONSTRAINT uq_db_environment_execution_id UNIQUE (id, execution_id),
    CONSTRAINT fk_db_environment_execution_execution FOREIGN KEY (execution_id) REFERENCES db_execution (id),
    CONSTRAINT fk_db_environment_execution_activity_env FOREIGN KEY (activity_env_id) REFERENCES db_activity_env (id),
    CONSTRAINT ck_db_environment_execution_status CHECK (status IN
                                                         ('PENDING', 'READY', 'BLOCKED', 'RUNNING', 'COMPLETED',
                                                          'FAILED', 'SKIPPED')),
    CONSTRAINT ck_db_environment_execution_order CHECK (execution_order > 0)
);

CREATE TABLE db_target_execution
(
    id                       VARCHAR(36) NOT NULL,
    environment_execution_id VARCHAR(36) NOT NULL,
    activity_env_source_id   VARCHAR(36) NOT NULL,
    status                   VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    worker_id                VARCHAR(128),
    queue_job_id             VARCHAR(128),
    attempt_number           INTEGER     NOT NULL DEFAULT 1,
    precheck_result          VARCHAR(16),
    started_at               TIMESTAMPTZ,
    ended_at                 TIMESTAMPTZ,
    result_summary           VARCHAR(2000),
    CONSTRAINT pk_db_target_execution PRIMARY KEY (id),
    CONSTRAINT uq_db_target_execution_source UNIQUE (environment_execution_id, activity_env_source_id, attempt_number),
    CONSTRAINT fk_db_target_execution_environment FOREIGN KEY (environment_execution_id) REFERENCES db_environment_execution (id),
    CONSTRAINT fk_db_target_execution_source FOREIGN KEY (activity_env_source_id) REFERENCES db_activity_env_source (id),
    CONSTRAINT ck_db_target_execution_status CHECK (status IN
                                                    ('PENDING', 'PRECHECK_RUNNING', 'PRECHECK_FAILED', 'BACKUP_RUNNING',
                                                     'BACKUP_COMPLETED', 'EXECUTING', 'VERIFYING', 'COMPLETED',
                                                     'FAILED', 'CANCEL_REQUESTED', 'CANCELLED')),
    CONSTRAINT ck_db_target_execution_precheck CHECK (precheck_result IS NULL OR precheck_result IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_db_target_execution_attempt CHECK (attempt_number > 0)
);

CREATE TABLE db_execution_step
(
    id                  VARCHAR(36) NOT NULL,
    target_execution_id VARCHAR(36) NOT NULL,
    script_id           VARCHAR(36) NOT NULL,
    step_order          SMALLINT    NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at          TIMESTAMPTZ,
    ended_at            TIMESTAMPTZ,
    duration_ms         BIGINT,
    rows_affected       BIGINT,
    error_code          VARCHAR(64),
    error_summary       VARCHAR(2000),
    result_artifact_key VARCHAR(512),
    CONSTRAINT pk_db_execution_step PRIMARY KEY (id),
    CONSTRAINT uq_db_execution_step_order UNIQUE (target_execution_id, step_order),
    CONSTRAINT fk_db_execution_step_target FOREIGN KEY (target_execution_id) REFERENCES db_target_execution (id),
    CONSTRAINT fk_db_execution_step_script FOREIGN KEY (script_id) REFERENCES db_script (id),
    CONSTRAINT ck_db_execution_step_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_db_execution_step_order CHECK (step_order > 0),
    CONSTRAINT ck_db_execution_step_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE TABLE db_execution_parameter
(
    id                 VARCHAR(36)  NOT NULL,
    execution_step_id  VARCHAR(36)  NOT NULL,
    parameter_name     VARCHAR(128) NOT NULL,
    data_type          VARCHAR(16)  NOT NULL,
    sensitive          BOOLEAN      NOT NULL DEFAULT FALSE,
    value_ciphertext   TEXT,
    encryption_key_ref VARCHAR(256),
    value_sha256       CHAR(64),
    CONSTRAINT pk_db_execution_parameter PRIMARY KEY (id),
    CONSTRAINT uq_db_execution_parameter UNIQUE (execution_step_id, parameter_name),
    CONSTRAINT fk_db_execution_parameter_step FOREIGN KEY (execution_step_id) REFERENCES db_execution_step (id),
    CONSTRAINT ck_db_execution_parameter_type CHECK (data_type IN
                                                     ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP')),
    CONSTRAINT ck_db_execution_parameter_sensitive
        CHECK ((sensitive = FALSE AND value_ciphertext IS NULL AND encryption_key_ref IS NULL)
            OR (sensitive = TRUE AND value_ciphertext IS NOT NULL AND encryption_key_ref IS NOT NULL))
);

CREATE TABLE db_execution_event
(
    id                  VARCHAR(36) NOT NULL,
    target_execution_id VARCHAR(36) NOT NULL,
    event_type          VARCHAR(64) NOT NULL,
    event_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id      VARCHAR(128),
    details_redacted    VARCHAR(2000),
    CONSTRAINT pk_db_execution_event PRIMARY KEY (id),
    CONSTRAINT fk_db_execution_event_target FOREIGN KEY (target_execution_id) REFERENCES db_target_execution (id)
);

CREATE TABLE db_backup
(
    id                  VARCHAR(36)   NOT NULL,
    target_execution_id VARCHAR(36)   NOT NULL,
    backup_type         VARCHAR(24)   NOT NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    storage_object_key  VARCHAR(1024) NOT NULL,
    size_bytes          BIGINT,
    record_count        BIGINT,
    checksum_sha256     CHAR(64),
    created_by          VARCHAR(128)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at         TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    CONSTRAINT pk_db_backup PRIMARY KEY (id),
    CONSTRAINT uq_db_backup_object UNIQUE (storage_object_key),
    CONSTRAINT fk_db_backup_target_execution FOREIGN KEY (target_execution_id) REFERENCES db_target_execution (id),
    CONSTRAINT ck_db_backup_type CHECK (backup_type IN ('TABLE', 'RECORD', 'FULL_EXPORT', 'SQL', 'CSV')),
    CONSTRAINT ck_db_backup_status CHECK (status IN
                                          ('PENDING', 'RUNNING', 'COMPLETED', 'VERIFIED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_db_backup_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT ck_db_backup_record_count CHECK (record_count IS NULL OR record_count >= 0),
    CONSTRAINT ck_db_backup_sha CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_recovery
(
    id                       VARCHAR(36)   NOT NULL,
    recovery_code            VARCHAR(32)   NOT NULL,
    originating_execution_id VARCHAR(36)   NOT NULL,
    status                   VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    reason                   VARCHAR(2000) NOT NULL,
    requested_by             VARCHAR(128)  NOT NULL,
    validation_sha256        CHAR(64),
    requested_at             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at               TIMESTAMPTZ,
    ended_at                 TIMESTAMPTZ,
    CONSTRAINT pk_db_recovery PRIMARY KEY (id),
    CONSTRAINT uq_db_recovery_code UNIQUE (recovery_code),
    CONSTRAINT fk_db_recovery_execution FOREIGN KEY (originating_execution_id) REFERENCES db_execution (id),
    CONSTRAINT ck_db_recovery_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'RUNNING', 'VERIFYING',
                                                       'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_db_recovery_sha CHECK (validation_sha256 IS NULL OR validation_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_recovery_target
(
    id                          VARCHAR(36) NOT NULL,
    recovery_id                 VARCHAR(36) NOT NULL,
    target_execution_id         VARCHAR(36) NOT NULL,
    backup_id                   VARCHAR(36),
    recovery_package_version_id VARCHAR(36),
    recovery_mode               VARCHAR(24) NOT NULL,
    status                      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result_sha256               CHAR(64),
    started_at                  TIMESTAMPTZ,
    ended_at                    TIMESTAMPTZ,
    CONSTRAINT pk_db_recovery_target PRIMARY KEY (id),
    CONSTRAINT fk_db_recovery_target_recovery FOREIGN KEY (recovery_id) REFERENCES db_recovery (id),
    CONSTRAINT fk_db_recovery_target_execution FOREIGN KEY (target_execution_id) REFERENCES db_target_execution (id),
    CONSTRAINT fk_db_recovery_target_backup FOREIGN KEY (backup_id) REFERENCES db_backup (id),
    CONSTRAINT fk_db_recovery_target_package FOREIGN KEY (recovery_package_version_id) REFERENCES db_script_package_version (id),
    CONSTRAINT ck_db_recovery_target_method
        CHECK ((backup_id IS NOT NULL AND recovery_package_version_id IS NULL)
            OR (backup_id IS NULL AND recovery_package_version_id IS NOT NULL)),
    CONSTRAINT ck_db_recovery_target_mode CHECK (recovery_mode IN
                                                 ('RESTORE', 'ROLLBACK_SQL', 'RECORD', 'TABLE', 'FLASHBACK')),
    CONSTRAINT ck_db_recovery_target_status CHECK (status IN ('PENDING', 'RUNNING', 'VERIFYING', 'COMPLETED', 'FAILED',
                                                              'CANCELLED')),
    CONSTRAINT ck_db_recovery_target_sha CHECK (result_sha256 IS NULL OR result_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_recovery_approval
(
    id                       VARCHAR(36)  NOT NULL,
    recovery_id              VARCHAR(36)  NOT NULL,
    approval_request_id      VARCHAR(128) NOT NULL,
    approval_status          VARCHAR(16)  NOT NULL,
    requested_by             VARCHAR(128) NOT NULL,
    submitted_at             TIMESTAMPTZ  NOT NULL,
    decided_at               TIMESTAMPTZ,
    approved_snapshot_sha256 CHAR(64)     NOT NULL,
    CONSTRAINT pk_db_recovery_approval PRIMARY KEY (id),
    CONSTRAINT uq_db_recovery_approval_request UNIQUE (approval_request_id),
    CONSTRAINT fk_db_recovery_approval_recovery FOREIGN KEY (recovery_id) REFERENCES db_recovery (id),
    CONSTRAINT ck_db_recovery_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_db_recovery_approval_sha CHECK (approved_snapshot_sha256 ~ '^[0-9a-fA-F]{64}$')
);

CREATE TABLE db_audit_event
(
    id               VARCHAR(36)  NOT NULL,
    event_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_type       VARCHAR(16)  NOT NULL,
    actor_id         VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    project_id       VARCHAR(36),
    activity_id      VARCHAR(36),
    execution_id     VARCHAR(36),
    correlation_id   VARCHAR(128),
    previous_status  VARCHAR(32),
    new_status       VARCHAR(32),
    details_redacted VARCHAR(2000),
    CONSTRAINT pk_db_audit_event PRIMARY KEY (id),
    CONSTRAINT fk_db_audit_project FOREIGN KEY (project_id) REFERENCES db_project (id),
    CONSTRAINT fk_db_audit_activity FOREIGN KEY (activity_id) REFERENCES db_activity (id),
    CONSTRAINT fk_db_audit_execution FOREIGN KEY (execution_id) REFERENCES db_execution (id),
    CONSTRAINT ck_db_audit_actor_type CHECK (actor_type IN ('USER', 'WORKER', 'SYSTEM'))
);

CREATE INDEX ix_db_project_member_project_enabled
    ON db_project_member (project_id, enabled);
CREATE INDEX ix_db_project_environment_project_enabled_order
    ON db_project_environment (project_id, enabled, promotion_order);
CREATE INDEX ix_db_project_env_datasource_enabled
    ON db_project_env_datasource (project_environment_id, enabled);
CREATE INDEX ix_db_project_env_datasource_source
    ON db_project_env_datasource (data_source_id);
CREATE INDEX ix_db_activity_project_created
    ON db_activity (project_id, created_at DESC);
CREATE INDEX ix_db_activity_status_updated
    ON db_activity (status, updated_at DESC);
CREATE INDEX ix_db_activity_env_activity_order
    ON db_activity_env (activity_id, execution_order);
CREATE INDEX ix_db_activity_env_source_order
    ON db_activity_env_source (activity_env_id, execution_order);
CREATE INDEX ix_db_execution_activity_requested
    ON db_execution (activity_id, requested_at DESC);
CREATE INDEX ix_db_execution_status_requested
    ON db_execution (status, requested_at);
CREATE INDEX ix_db_environment_execution_status
    ON db_environment_execution (execution_id, status);
CREATE INDEX ix_db_target_execution_status
    ON db_target_execution (environment_execution_id, status);
CREATE INDEX ix_db_execution_step_target_order
    ON db_execution_step (target_execution_id, step_order);
CREATE INDEX ix_db_execution_event_target_time
    ON db_execution_event (target_execution_id, event_at);
CREATE INDEX ix_db_backup_target_created
    ON db_backup (target_execution_id, created_at DESC);
CREATE INDEX ix_db_backup_status_expiry
    ON db_backup (status, expires_at);
CREATE INDEX ix_db_recovery_origin_created
    ON db_recovery (originating_execution_id, requested_at DESC);
CREATE INDEX ix_db_recovery_target_recovery
    ON db_recovery_target (recovery_id);
CREATE INDEX ix_db_audit_event_time
    ON db_audit_event (event_at DESC);
CREATE INDEX ix_db_audit_project_time
    ON db_audit_event (project_id, event_at DESC);
CREATE INDEX ix_db_audit_activity_time
    ON db_audit_event (activity_id, event_at DESC);
