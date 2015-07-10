#!/bin/sh

ENGINE_PROLOG="${ENGINE_PREFIX:-$(dirname $0)/..}/share/ovirt-engine/bin/engine-prolog.sh"
if [ ! -f ${ENGINE_PROLOG} ]; then
    echo \
"Cannot locate engine-prolog.sh, please specify oVirt engine installation \
prefix. For example:

  ENGINE_PREFIX=\$HOME/ovirt-engine ovirt-aaa-jdbc-tool

"
    exit -1
fi

. "${ENGINE_PROLOG}"

exec "${JAVA_HOME}/bin/java" \
	-Djava.security.auth.login.config="${ENGINE_USR}/conf/jaas.conf" \
	-Djava.util.logging.config.file="${OVIRT_LOGGING_PROPERTIES}" \
	-Djboss.modules.write-indexes=false \
	-Dorg.ovirt.engine.aaa.jdbc.programName="${0}" \
	-jar "${JBOSS_HOME}/jboss-modules.jar" \
	-dependencies org.ovirt.engine.extension.aaa.jdbc \
	-class org.ovirt.engine.extension.aaa.jdbc.binding.cli.Cli \
	"$@"
