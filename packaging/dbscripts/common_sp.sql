--------------------------------------------------
-- DB helper functions
--------------------------------------------------

-- Creates a column in the given table (if not exists)
Create or replace FUNCTION fn_db_add_column(v_table varchar(128), v_column varchar(128), v_column_def text)
returns void
AS $procedure$
declare
v_sql text;

begin
	if (not exists (select 1 from information_schema.columns where table_name ilike v_table and column_name ilike v_column)) then
	    begin
		v_sql := 'ALTER TABLE ' || v_table || ' ADD COLUMN ' || v_column || ' ' || v_column_def;
		EXECUTE v_sql;
            end;
	end if;
END; $procedure$
LANGUAGE plpgsql;

-- delete a column from a table and all its dependencied
Create or replace FUNCTION fn_db_drop_column(v_table varchar(128), v_column varchar(128))
returns void
AS $procedure$
declare
v_sql text;
begin
        if (exists (select 1 from information_schema.columns where table_name ilike v_table and column_name ilike v_column)) then
            begin
                v_sql := 'ALTER TABLE ' || v_table || ' DROP COLUMN ' || v_column;
                EXECUTE v_sql;
            end;
        else
            RAISE EXCEPTION 'Table % or Column % does not exist.', v_table, v_column;
        end if;
end;$procedure$
LANGUAGE plpgsql;

-- Changes a column data type (if value conversion is supported)
Create or replace FUNCTION fn_db_change_column_type(v_table varchar(128), v_column varchar(128),
                                                    v_type varchar(128), v_new_type varchar(128))
returns void
AS $procedure$
declare
v_sql text;

begin
	if (exists (select 1 from information_schema.columns where table_name ilike v_table and column_name ilike v_column and (udt_name ilike v_type or data_type ilike v_type))) then
	    begin
		v_sql := 'ALTER TABLE ' || v_table || ' ALTER COLUMN ' || v_column || ' TYPE ' || v_new_type;
		EXECUTE v_sql;
            end;
            --- ignore operation if requested type is already there
        elsif (not exists (select 1 from information_schema.columns where table_name ilike v_table and column_name ilike v_column and
                (udt_name ilike v_new_type or data_type ilike v_new_type))) then
            RAISE EXCEPTION 'Table % or Column % does not exist.', v_table, v_column;
	end if;
END; $procedure$
LANGUAGE plpgsql;

-- rename a column for a given table
Create or replace FUNCTION fn_db_rename_column(v_table varchar(128), v_column varchar(128), v_new_name varchar(128))
returns void
AS $procedure$
declare
v_sql text;

begin
	if (exists (select 1 from information_schema.columns where table_name ilike v_table and column_name ilike v_column)) then
	    begin
		v_sql := 'ALTER TABLE ' || v_table || ' RENAME COLUMN ' || v_column || ' TO ' || v_new_name;
		EXECUTE v_sql;
            end;
        else
            RAISE EXCEPTION 'Table % or Column % does not exist.', v_table, v_column;
	end if;
END; $procedure$
LANGUAGE plpgsql;



create or replace function fn_db_create_constraint (
    v_table varchar(128), v_constraint varchar(128), v_constraint_sql text)
returns void
AS $procedure$
begin
    if  NOT EXISTS (SELECT 1 from pg_constraint where conname ilike v_constraint) then
        execute 'ALTER TABLE ' || v_table ||  ' ADD CONSTRAINT ' || v_constraint || ' ' || v_constraint_sql;
    end if;
END; $procedure$
LANGUAGE plpgsql;

create or replace function fn_db_drop_constraint (
    v_table varchar(128), v_constraint varchar(128))
returns void
AS $procedure$
begin
    if  EXISTS (SELECT 1 from pg_constraint where conname ilike v_constraint) then
        execute 'ALTER TABLE ' || v_table ||  ' DROP CONSTRAINT ' || v_constraint || ' CASCADE';
    end if;
END; $procedure$
LANGUAGE plpgsql;

--------------------------------------------------
-- End of DB helper functions
--------------------------------------------------

Create or replace FUNCTION CheckDBConnection() RETURNS SETOF integer IMMUTABLE
   AS $procedure$
BEGIN
    RETURN QUERY SELECT 1;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION generate_drop_all_functions_syntax() RETURNS SETOF text STABLE
   AS $procedure$
BEGIN
RETURN QUERY select 'drop function if exists ' || ns.nspname || '.' || proname || '(' || oidvectortypes(proargtypes) || ') cascade;' from pg_proc inner join pg_namespace ns on (pg_proc.pronamespace=ns.oid) where ns.nspname = '@SCHEMA_NAME@' order by proname;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION generate_drop_all_views_syntax() RETURNS SETOF text STABLE
   AS $procedure$
BEGIN
RETURN QUERY select 'DROP VIEW if exists ' || table_schema || '.' || table_name || ' CASCADE;' from information_schema.views where table_schema = '@SCHEMA_NAME@' order by table_name;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION generate_drop_all_tables_syntax() RETURNS SETOF text STABLE
   AS $procedure$
BEGIN
RETURN QUERY select 'DROP TABLE if exists ' || table_schema || '.' || table_name || ' CASCADE;' from information_schema.tables where table_schema = '@SCHEMA_NAME@' and table_type = 'BASE TABLE' order by table_name;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION generate_drop_all_seq_syntax() RETURNS SETOF text STABLE
   AS $procedure$
BEGIN
RETURN QUERY select 'DROP SEQUENCE if exists ' || sequence_schema || '.' || sequence_name || ' CASCADE;' from information_schema.sequences  where sequence_schema = '@SCHEMA_NAME@' order by sequence_name;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION generate_drop_all_user_types_syntax() RETURNS SETOF text STABLE
   AS $procedure$
BEGIN
RETURN QUERY SELECT 'DROP TYPE if exists ' || c.relname::information_schema.sql_identifier || ' CASCADE;'
   FROM pg_namespace n, pg_class c, pg_type t
   WHERE n.oid = c.relnamespace and t.typrelid = c.oid and c.relkind = 'c'::"char" and
   n.nspname = '@SCHEMA_NAME@'
   ORDER BY  c.relname::information_schema.sql_identifier;
END; $procedure$
LANGUAGE plpgsql;

Create or replace FUNCTION fn_get_column_size( v_table varchar(64), v_column varchar(64)) returns integer STABLE
   AS $procedure$
   declare
   retvalue  integer;
BEGIN
   retvalue := character_maximum_length from information_schema.columns
    where
    table_name ilike v_table and column_name ilike v_column and
    table_schema = '@SCHEMA_NAME@' and udt_name in ('char','varchar');
   return retvalue;
END; $procedure$
LANGUAGE plpgsql;

-- The following function accepts a table or view object
-- Values of columns not matching the ones stored for this object in object_column_white_list table
-- will be masked with an empty value.
CREATE OR REPLACE FUNCTION fn_db_mask_object(v_object regclass) RETURNS setof record as
$BODY$
DECLARE
    v_sql TEXT;
    v_table record;
    v_table_name TEXT;
    temprec record;
BEGIN
    -- get full table/view name from v_object (i.e <namespace>.<name>)
    select c.relname, n.nspname INTO v_table
        FROM pg_class c join pg_namespace n on c.relnamespace = n.oid WHERE c.oid = v_object;
    -- try to get filtered query syntax from previous execution
    if exists (select 1 from object_column_white_list_sql where object_name = v_table.relname) then
	select sql into v_sql from object_column_white_list_sql where object_name = v_table.relname;
    else
        v_table_name := quote_ident( v_table.nspname ) || '.' || quote_ident( v_table.relname );
        -- compose sql statement while skipping values for columns not defined in object_column_white_list for this table.
        for temprec in select a.attname, t.typname
                       FROM pg_attribute a join pg_type t on a.atttypid = t.oid
                       WHERE a.attrelid = v_object AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum
        loop
            v_sql := coalesce( v_sql || ', ', 'SELECT ' );
            if exists(select 1 from object_column_white_list
               where object_name = v_table.relname and column_name = temprec.attname) then
               v_sql := v_sql || quote_ident( temprec.attname );
            ELSE
               v_sql := v_sql || 'NULL::' || quote_ident( temprec.typname ) || ' as ' || quote_ident( temprec.attname );
            END IF;
        END LOOP;
        v_sql := v_sql || ' FROM ' || v_table_name;
        v_sql := 'SELECT x::' || v_table_name || ' as rec FROM (' || v_sql || ') as x';
        -- save generated query for further use
        insert into object_column_white_list_sql(object_name,sql) values (v_table.relname, v_sql);
    end if;
    RETURN QUERY EXECUTE v_sql;
END; $BODY$
LANGUAGE plpgsql;

-- Checks if a table given by its name exists in DB
CREATE OR REPLACE FUNCTION fn_db_is_table_exists (v_table varchar(64)) returns boolean STABLE
   AS $procedure$
   declare
   retvalue  boolean;
BEGIN
   retvalue := EXISTS (
        SELECT * FROM information_schema.tables WHERE table_schema = '@SCHEMA_NAME@' AND table_name ILIKE v_table
   );
   return retvalue;
END; $procedure$
LANGUAGE plpgsql;


-- Creates an index on an existing table, if there is no WHERE condition, the last argument should be empty ('')
-- Example : Table T with columns a,b and c
-- fn_db_create_index('T_INDEX', 'T', 'a,b', ''); ==> Creates an index named T_INDEX on table T (a,b)
create or replace FUNCTION fn_db_create_index(v_index_name varchar(128), v_table_name varchar(128), v_column_names text, v_where_predicate text)
returns void
AS $procedure$
DECLARE
    v_sql TEXT;
BEGIN
    v_sql := 'DROP INDEX ' || ' IF EXISTS ' || v_index_name || '; CREATE INDEX ' || v_index_name || ' ON ' || v_table_name || '(' || v_column_names || ')';
    IF v_where_predicate = '' THEN
        v_sql := v_sql || ';';
    ELSE
        v_sql := v_sql || ' WHERE ' || v_where_predicate || ';';
    END IF;
    EXECUTE v_sql;
END; $procedure$
LANGUAGE plpgsql;

-- sets the v_val value for v_option_name in vdc_options for all versions before and up to v_version including v_version
-- please note that versions must be insync with  org.ovirt.engine.core.compat.Version
create or replace FUNCTION  fn_db_add_config_value_for_versions_up_to(v_option_name varchar(100), v_val varchar(4000), v_version varchar(40))
returns void
AS $procedure$
declare
    i   int;
    arr varchar[] := array['3.0', '3.1', '3.2', '3.3', '3.4', '3.5', '3.6'];
begin
    FOR i IN array_lower(arr, 1) .. array_upper(arr, 1)
    LOOP
        PERFORM fn_db_add_config_value(v_option_name, v_val, arr[i]);
        EXIT WHEN  arr[i] = v_version;
    END LOOP;
END; $procedure$
LANGUAGE plpgsql;


