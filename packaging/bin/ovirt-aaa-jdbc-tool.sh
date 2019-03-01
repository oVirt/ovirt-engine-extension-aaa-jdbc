#!/bin/sh

. "$(dirname "$(readlink -f "$0")")"/../../ovirt-engine/bin/engine-prolog.sh

"${JAVA_HOME}/bin/java" \
	-Djava.security.auth.login.config="${ENGINE_USR}/conf/jaas.conf" \
	-Djava.util.logging.config.file="${OVIRT_LOGGING_PROPERTIES}" \
	-Djboss.modules.write-indexes=false \
	-Dorg.ovirt.engine.aaa.jdbc.programName="${0}" \
	-Dorg.ovirt.engine.aaa.jdbc.engineEtc="${ENGINE_ETC}" \
	-jar "${JBOSS_HOME}/jboss-modules.jar" \
	-dependencies org.ovirt.engine.extension.aaa.jdbc \
	-class org.ovirt.engine.extension.aaa.jdbc.binding.cli.Cli \
	"$@"

# Log message about execution
rc=$?
result=$([[ ${rc} == 0 ]] && echo -n "successfully." || echo -n "with failure.")
params="$(echo "$@" | sed 's/password=pass\:[^[:space:]]*/password=pass\:*** /g')"
logger "User '${USER}' executed '$0 ${params}' ${result}"
