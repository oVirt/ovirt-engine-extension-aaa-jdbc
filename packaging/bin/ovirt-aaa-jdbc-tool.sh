#!/bin/sh

. "$(dirname "$(readlink -f "$0")")"/../../ovirt-engine/bin/engine-prolog.sh

exec "${JAVA_HOME}/bin/java" \
	-Djava.security.auth.login.config="${ENGINE_USR}/conf/jaas.conf" \
	-Djava.util.logging.config.file="${OVIRT_LOGGING_PROPERTIES}" \
	-Djboss.modules.write-indexes=false \
	-Dorg.ovirt.engine.aaa.jdbc.programName="${0}" \
	-Dorg.ovirt.engine.aaa.jdbc.engineEtc="${ENGINE_ETC}" \
	-jar "${JBOSS_HOME}/jboss-modules.jar" \
	-dependencies org.ovirt.engine.extension.aaa.jdbc \
	-class org.ovirt.engine.extension.aaa.jdbc.binding.cli.Cli \
	"$@"
