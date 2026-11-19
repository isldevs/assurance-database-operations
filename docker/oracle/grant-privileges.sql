
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT FAILURE

SET SERVEROUTPUT ON
SET VERIFY OFF
SET FEEDBACK ON

ALTER SESSION SET CONTAINER = CDB$ROOT;

-- Ensure the PDB is open.
DECLARE
    v_open_mode VARCHAR2(20);
BEGIN
SELECT OPEN_MODE
INTO v_open_mode
FROM V$PDBS
WHERE NAME = 'FREEPDB1';

IF v_open_mode = 'MOUNTED' THEN
EXECUTE IMMEDIATE
    'ALTER PLUGGABLE DATABASE FREEPDB1 OPEN READ WRITE';
ELSIF v_open_mode <> 'READ WRITE' THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'Unexpected PDB state: ' || v_open_mode
        );
END IF;
END;
/

ALTER PLUGGABLE DATABASE FREEPDB1 SAVE STATE;

ALTER SESSION SET CONTAINER = FREEPDB1;

PROMPT ========================================
PROMPT INITIALIZING DATABASE SCHEMAS
PROMPT ========================================

DECLARE
    PROCEDURE provision_schema(p_username VARCHAR2) IS
    v_count NUMBER;
BEGIN
SELECT COUNT(*)
INTO v_count
FROM DBA_USERS
WHERE USERNAME = p_username;

IF v_count = 0 THEN
EXECUTE IMMEDIATE
    'CREATE USER ' || p_username ||
                ' IDENTIFIED BY "&1"' ||
                ' DEFAULT TABLESPACE USERS' ||
                ' TEMPORARY TABLESPACE TEMP' ||
                ' QUOTA 100M ON USERS';

DBMS_OUTPUT.PUT_LINE(
                '[CREATED] ' || p_username
            );
ELSE
            DBMS_OUTPUT.PUT_LINE(
                '[EXISTS] ' || p_username
            );
END IF;

EXECUTE IMMEDIATE
    'ALTER USER ' || p_username ||
            ' ACCOUNT UNLOCK';

EXECUTE IMMEDIATE
    'GRANT CREATE SESSION,
                   CREATE TABLE,
                   CREATE VIEW,
                   CREATE SEQUENCE,
                   CREATE PROCEDURE,
                   CREATE TRIGGER,
                   CREATE TYPE,
                   CREATE SYNONYM,
                   CREATE MATERIALIZED VIEW
             TO ' || p_username;

DBMS_OUTPUT.PUT_LINE(
            '[GRANTED] ' || p_username
        );
END;

BEGIN
provision_schema('DBOPS');
provision_schema('MWING');
provision_schema('PERSONA');
provision_schema('PAYMENT');
END;
/

COLUMN USERNAME FORMAT A20
COLUMN ACCOUNT_STATUS FORMAT A20

SELECT USERNAME, ACCOUNT_STATUS
FROM DBA_USERS
WHERE USERNAME IN (
                   'DBOPS',
                   'MWING',
                   'PERSONA',
                   'PAYMENT'
    )
ORDER BY USERNAME;

PROMPT ========================================
PROMPT INITIALIZATION COMPLETED
PROMPT ========================================

UNDEFINE 1
EXIT SUCCESS
