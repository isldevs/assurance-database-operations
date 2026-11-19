#!/bin/bash

set -euo pipefail

export ORACLE_SID=FREE

: "${ORACLE_PASSWORD:?ORACLE_PASSWORD is required}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/grant-privileges.sql"

if [[ ! -r "$SQL_FILE" ]]; then
    echo "[ERROR] SQL file not found: $SQL_FILE"
    exit 1
fi

if [[ ! "$ORACLE_PASSWORD" =~ ^[a-zA-Z0-9_@#.-]+$ ]]; then
    echo "[ERROR] Unsupported password characters"
    exit 1
fi

echo "[INFO] Waiting for Oracle database..."

ready=false

for ((attempt=1; attempt<=60; attempt++)); do

    if result=$(sqlplus -L -s / as sysdba <<'SQL'
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT FAILURE

SET HEADING OFF
SET FEEDBACK OFF
SET PAGESIZE 0

SELECT OPEN_MODE
FROM V$PDBS
WHERE NAME = 'FREEPDB1';

EXIT SUCCESS
SQL
    ); then
        if [[ "$result" == *"READ WRITE"* ]]; then
            ready=true
            break
        fi
    fi

    echo "[WAIT] Oracle not ready ($attempt/60)"
    sleep 5
done

if [[ "$ready" != "true" ]]; then
    echo "[ERROR] Oracle database readiness timeout"
    exit 1
fi

echo "[INFO] Oracle database is ready"
echo "[INFO] Initializing database schemas..."

sqlplus -L -s / as sysdba @"$SQL_FILE" "$ORACLE_PASSWORD"

echo "[SUCCESS] Database schemas initialized"
