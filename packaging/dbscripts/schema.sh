#!/bin/sh

DBFUNC_COMMON_DBSCRIPTS_DIR="$(dirname "$0")"
. "${DBFUNC_COMMON_DBSCRIPTS_DIR}/dbfunc-custom.sh"

cleanup() {
	dbfunc_cleanup
}
trap cleanup 0
dbfunc_init

usage() {
	cat << __EOF__
Usage: $0 [options]

    -h            - This help text.
    -v            - Turn on verbosity                         (WARNING: lots of output)
    -l LOGFILE    - The logfile for capturing output          (def. ${DBFUNC_LOGFILE})
    -s HOST       - The database servername for the database  (def. ${DBFUNC_DB_HOST})
    -p PORT       - The database port for the database        (def. ${DBFUNC_DB_PORT})
    -u USER       - The username for the database             (def. ${DBFUNC_DB_USER})
    -d DATABASE   - The database name                         (def. ${DBFUNC_DB_DATABASE})
    -m MD5FILE    - Where to store schema MD5 files           (def. ${DBFUNC_COMMON_MD5FILE})
    -c COMMAND    - Command: apply|refresh|drop
    -T            - Upload data used for tests execution      (def. ${DBFUNC_TESTING_DB})

__EOF__
}

while getopts hvl:s:p:u:d:m:c:T option; do
	case $option in
		\?) usage; exit 1;;
		h) usage; exit 0;;
		v) DBFUNC_VERBOSE=1;;
		l) DBFUNC_LOGFILE="${OPTARG}";;
		s) DBFUNC_DB_HOST="${OPTARG}";;
		p) DBFUNC_DB_PORT="${OPTARG}";;
		u) DBFUNC_DB_USER="${OPTARG}";;
		d) DBFUNC_DB_DATABASE="${OPTARG}";;
		m) DBFUNC_COMMON_MD5FILE="${OPTARG}";;
		c) COMMAND="${OPTARG}";;
		T) DBFUNC_TESTING_DB=1;;
	esac
done

case "${COMMAND}" in
	apply|refresh|drop) ;;
	'') die "Please specify command";;
	*) die "Invalid command '${COMMAND}'";;
esac

eval dbfunc_common_schema_${COMMAND}
