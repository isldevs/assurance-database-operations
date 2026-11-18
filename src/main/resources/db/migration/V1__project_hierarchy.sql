-- Metadata model for: one project → many environments → many data sources.
-- Portable SQL so the same Flyway scripts can run locally (Docker Postgres) and in production.

CREATE TABLE db_environment (
    code            VARCHAR(16)  NOT NULL,
    description     VARCHAR(128) NOT NULL,
    sort_order      INTEGER      NOT NULL,
    CONSTRAINT pk_db_environment PRIMARY KEY (code)
);

INSERT INTO db_environment (code, description, sort_order) VALUES
    ('UAT', 'User acceptance testing', 1),
    ('STAGE', 'Staging environment', 2),
    ('STAGE2', 'Secondary staging environment', 3),
    ('STAGE3', 'Additional staging environment', 4);

CREATE TABLE db_project (
    id              CHAR(36)     NOT NULL,
    project_code    VARCHAR(64)  NOT NULL,
    project_name    VARCHAR(128) NOT NULL,
    owner_name      VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_db_project PRIMARY KEY (id),
    CONSTRAINT uq_db_project_code UNIQUE (project_code),
    CONSTRAINT ck_db_project_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE db_project_environment (
    id                  CHAR(36)    NOT NULL,
    project_id          CHAR(36)    NOT NULL,
    environment_code    VARCHAR(16) NOT NULL,
    enabled             BOOLEAN     NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_db_project_environment PRIMARY KEY (id),
    CONSTRAINT uq_db_project_environment UNIQUE (project_id, environment_code),
    CONSTRAINT fk_db_project_environment_project
        FOREIGN KEY (project_id) REFERENCES db_project (id),
    CONSTRAINT fk_db_project_environment_env
        FOREIGN KEY (environment_code) REFERENCES db_environment (code)
);

CREATE TABLE db_project_env_datasource (
    id                      CHAR(36)     NOT NULL,
    project_environment_id  CHAR(36)     NOT NULL,
    data_source_code        VARCHAR(64)  NOT NULL,
    engine                  VARCHAR(32)  NOT NULL,
    schema_name             VARCHAR(128),
    jdbc_url                VARCHAR(512) NOT NULL,
    username                VARCHAR(128) NOT NULL,
    secret_ref              VARCHAR(128) NOT NULL,
    enabled                 BOOLEAN      NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_db_project_env_datasource PRIMARY KEY (id),
    CONSTRAINT uq_db_project_env_datasource UNIQUE (project_environment_id, data_source_code),
    CONSTRAINT fk_db_project_env_datasource_env
        FOREIGN KEY (project_environment_id) REFERENCES db_project_environment (id),
    CONSTRAINT ck_db_project_env_datasource_engine CHECK (engine IN ('ORACLE', 'POSTGRESQL'))
);

CREATE INDEX ix_db_project_env_datasource_code
    ON db_project_env_datasource (data_source_code);
