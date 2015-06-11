
. "${DBFUNC_COMMON_DBSCRIPTS_DIR}/dbfunc-common.sh"

DBFUNC_DB_USER="${DBFUNC_DB_USER:-engine}"
DBFUNC_DB_DATABASE="${DBFUNC_DB_DATABASE:-engine}"

dbfunc_common_hook_init_insert_data() {
	# Apply changes to database
	for script in $(ls "${DBFUNC_COMMON_DBSCRIPTS_DIR}"/data/*insert_*.sql); do
		echo "Inserting data from ${script} ..."
		dbfunc_psql_die --file="${script}" > /dev/null
	done

        # Insert testing data
        if [ -n "${DBFUNC_TESTING_DB}" ]; then
		for script in $(ls "${DBFUNC_COMMON_DBSCRIPTS_DIR}"/test-data/*.sql); do
			echo "Inserting data from ${script} ..."
			dbfunc_psql_die --file="${script}" > /dev/null
		done
	fi
}

dbfunc_common_hook_pre_upgrade() {
	return
}

#refreshes views
dbfunc_common_hook_views_refresh() {
	return
}

# Materilized views functions, override with empty implementation on DBs that not supporting that

dbfunc_common_hook_materialized_views_install() {
	return
}

dbfunc_common_hook_materialized_views_drop() {
	return
}

dbfunc_common_hook_materialized_viewsrefresh_() {
	return
}

dbfunc_common_hook_sequence_numbers_update() {
	dbfunc_psql_die --file="${DBFUNC_COMMON_DBSCRIPTS_DIR}/update_sequence_numbers.sql" > /dev/null    
}
