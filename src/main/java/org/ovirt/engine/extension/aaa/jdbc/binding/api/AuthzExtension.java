/*
 * Copyright 2012-2014 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package org.ovirt.engine.extension.aaa.jdbc.binding.api;

import static org.ovirt.engine.api.extensions.aaa.Authz.QueryFilterOperator;
import static org.ovirt.engine.api.extensions.aaa.Authz.QueryFilterRecord;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.ExtUUID;
import org.ovirt.engine.api.extensions.Extension;
import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.binding.Config;
import org.ovirt.engine.extension.aaa.jdbc.core.Authorization;
import org.ovirt.engine.extension.aaa.jdbc.core.Schema;
import org.ovirt.engine.extension.aaa.jdbc.core.Tasks;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthzExtension implements Extension {
    private static final Logger LOG = LoggerFactory.getLogger(AuthzExtension.class);
    private static final int MAX_FILTER_SIZE = 100; // Maximum field nest level.

    private Tasks tasks = null;
    private Authorization authorization = null;
    private Properties configuration;

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        Integer baseResult = null;
        output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED);
        output.put(Authz.InvokeKeys.STATUS, Authz.Status.GENERAL_ERROR);

        try {
            if (tasks != null) {
                tasks.execute();
            }
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                doLoad(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.INITIALIZE)) {
                doInit(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.TERMINATE)) {
                //Nothing to do.
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.FETCH_PRINCIPAL_RECORD)) {
                doFetchPrincipalRecord(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_OPEN)) {
                doQueryOpen(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_EXECUTE)) {
                doQueryExecute(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_CLOSE)) {
                doQueryClose(input, output);
            } else {
                baseResult = Base.InvokeResult.UNSUPPORTED;
                throw new IllegalArgumentException();
            }
        } catch (Throwable e) {
            LOG.error(
                "Unexpected Exception invoking: {}",
                input.get(Base.InvokeKeys.COMMAND)
            );
            LOG.debug(
                "Exception:",
                e
            );
            output.put(Base.InvokeKeys.RESULT, baseResult != null ? baseResult: Authz.Status.GENERAL_ERROR);
            output.put(Authz.InvokeKeys.STATUS, Authz.Status.GENERAL_ERROR);
        }
    }

    /**
     * Loads extension instance.
     * Extension should configure its information within the context during this command.
     * No operation that may fail or change system state should be carried out at this stage.
     */
    private void doLoad(ExtMap input, ExtMap output) throws Exception {
        input.get(
            Base.InvokeKeys.CONTEXT, ExtMap.class
        ).mput(
            Authz.ContextKeys.AVAILABLE_NAMESPACES,
            Arrays.asList(Global.NAMESPACE)
        ).mput(
            Base.ContextKeys.EXTENSION_NAME,
            Config.PACKAGE_NAME.concat(".authz")
        ).mput(
            Authz.ContextKeys.CAPABILITIES,
            Authz.Capabilities.RECURSIVE_GROUP_RESOLUTION
        ).mput(
            Authz.ContextKeys.QUERY_MAX_FILTER_SIZE,
            MAX_FILTER_SIZE
        ).mput(
            ExtensionUtils.JDBC_INFO
        );

        configuration =
            ExtensionUtils.loadIncludedConfiguration(
                input.get(
                    Base.InvokeKeys.CONTEXT,
                    ExtMap.class
                )
            );

        output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
    }

    private void doInit(ExtMap input, ExtMap output) {
        DataSource ds = new DataSourceProvider(configuration).provide();
        this.authorization = new Authorization(ds);
        this.tasks = new Tasks(ds, this.authorization);
        output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
    }

    private void doFetchPrincipalRecord(ExtMap input, ExtMap output) throws SQLException {
        int flags = input.get(Authz.InvokeKeys.QUERY_FLAGS, Integer.class, 0);
        Collection<ExtMap> principals = authorization.getResults(
            Formatter.format(
                "{} = '{}'",
                Schema.SEARCH_KEYS.get(Authz.PrincipalRecord.NAME),
                input.containsKey(Authz.InvokeKeys.PRINCIPAL) ?
                input.get(Authz.InvokeKeys.PRINCIPAL, String.class) :
                input.get(Authn.InvokeKeys.AUTH_RECORD, ExtMap.class)
                .get(Authn.AuthRecord.PRINCIPAL, String.class)
            ),
            new ExtMap().mput(
                Global.SearchContext.IS_PRINCIPAL,
                true
            ).mput(
                Global.SearchContext.WITH_GROUPS,
                (flags & Authz.QueryFlags.RESOLVE_GROUPS) != 0
            ).mput(
                Global.SearchContext.RECURSIVE,
                (flags & Authz.QueryFlags.RESOLVE_GROUPS_RECURSIVE) != 0
            ).mput(
                Global.SearchContext.PAGE_SIZE,
                1
            )
        );
        output.put(Authz.InvokeKeys.PRINCIPAL_RECORD,
            principals.size() > 0 ?
            principals.iterator().next() :
            null
        );
        output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        output.put(Authz.InvokeKeys.STATUS, Authz.Status.SUCCESS);
    }

    private void doQueryOpen(ExtMap input, ExtMap output) throws SQLException {
        try {
            int flags = input.get(Authz.InvokeKeys.QUERY_FLAGS, Integer.class, 0);
            output.mput(
                Authz.InvokeKeys.QUERY_OPAQUE,
                authorization.openQuery(
                    parse(
                        input.get(
                            Authz.InvokeKeys.QUERY_FILTER,
                            ExtMap.class
                        ),
                        false
                    ),
                    new ExtMap().mput(
                        Global.SearchContext.IS_PRINCIPAL,
                        input.get(Authz.InvokeKeys.QUERY_ENTITY, ExtUUID.class)
                            .equals(Authz.QueryEntity.PRINCIPAL)
                    ).mput(
                        Global.SearchContext.WITH_GROUPS,
                        (flags & Authz.QueryFlags.RESOLVE_GROUPS) != 0
                    ).mput(
                        Global.SearchContext.RECURSIVE,
                        (flags & Authz.QueryFlags.RESOLVE_GROUPS_RECURSIVE) != 0
                    )
                )
            ).mput(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        }  catch (IllegalArgumentException e) {
            output.mput(Authz.InvokeKeys.STATUS, Authz.Status.GENERAL_ERROR)
            .mput(Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED)
            .mput(Base.InvokeKeys.MESSAGE, e.getMessage());
        }
        output.mput(Authz.InvokeKeys.STATUS, Authz.Status.SUCCESS);

    }

    private void doQueryExecute(ExtMap input, ExtMap output) throws SQLException {
        output.mput(
            Authz.InvokeKeys.QUERY_RESULT,
            authorization.executeQuery(
                input.get(Authz.InvokeKeys.QUERY_OPAQUE, String.class),
                input.get(Authz.InvokeKeys.PAGE_SIZE, Integer.class, Integer.MAX_VALUE)
            )
        ).mput(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS)
        .mput(Authz.InvokeKeys.STATUS, Authz.Status.SUCCESS);
    }

    private void doQueryClose(ExtMap input, ExtMap output) throws SQLException {
        authorization.closeQuery(input.get(Authz.InvokeKeys.QUERY_OPAQUE, String.class));
        output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        output.put(Authz.InvokeKeys.STATUS, Authz.Status.SUCCESS);
    }

    private String parse(ExtMap filter, boolean orNull) {
        ExtKey key = filter.get(QueryFilterRecord.KEY, ExtKey.class);
        int opCode = filter.get(QueryFilterRecord.OPERATOR, Integer.class);

        StringBuilder sb = new StringBuilder();
        if (
            opCode == QueryFilterOperator.EQ ||
            opCode == QueryFilterOperator.GE ||
            opCode == QueryFilterOperator.LE
        ){ // field filter
            String val = filter.get(key, String.class);

            if (!Global.SEARCH_PATTERN.matcher(val).matches()) {
                throw new IllegalArgumentException(
                    Formatter.format(
                        "attribute value does not match pattern attribute name: {}, value: {} pattern: {}",
                        key.getUuid().getName(),
                        val,
                        Global.SEARCH_PATTERN
                    )
                );
            }
            if (
                opCode == QueryFilterOperator.EQ &&
                val.endsWith("*")
            ) {
                val = val.substring(0, val.length() - 1).concat("%");
                opCode = Schema.AuthzInternal.LIKE;
            }
            sb.append(
                Formatter.format(
                    "({} {} '{}' {})",
                    Schema.SEARCH_KEYS.get(key),
                    Schema.OPERATORS.get(opCode),
                    val,
                    orNull ?
                    Formatter.format(
                        " OR {} is null ",
                        Schema.SEARCH_KEYS.get(key)
                    ) :
                    ""
                )
            );
        } else { // nested filter
            Collection<ExtMap> extMaps = filter.get(QueryFilterRecord.FILTER);
            ExtMap[] filters  = extMaps.toArray(new ExtMap[extMaps.size()]);
            boolean not = false;
            for (int i = 0; i < filters.length; i++) {
                if (i == 0 && opCode == QueryFilterOperator.NOT) {
                    sb.append(Schema.OPERATORS.get(QueryFilterOperator.NOT)).append(' ');
                    not = true;
                }
                sb.append(parse(filters[i], not));

                if (i < filters.length - 1) {
                    sb.append(Schema.OPERATORS.get(opCode)).append(' ');
                }
            }
        }
        return sb.length() > 0 ?
        sb.toString() :
        "0";
    }
}
