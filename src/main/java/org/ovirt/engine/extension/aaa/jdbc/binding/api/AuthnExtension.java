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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Properties;

import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.ExtUUID;
import org.ovirt.engine.api.extensions.Extension;
import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.extension.aaa.jdbc.binding.Config;
import org.ovirt.engine.extension.aaa.jdbc.core.Authentication;
import org.ovirt.engine.extension.aaa.jdbc.core.Tasks;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthnExtension implements Extension {
    private static final Logger LOG = LoggerFactory.getLogger(AuthnExtension.class);

    private Authentication authentication;
    private Tasks tasks = null;
    private Properties configuration;

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        try {
            if (tasks != null) {
                tasks.execute();
            }
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                doLoad(input);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.INITIALIZE)) {
                doInit(input);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.TERMINATE)) {
                // Nothing to do.
            } else if (
                (input.get(Base.InvokeKeys.COMMAND).equals(Authn.InvokeCommands.AUTHENTICATE_CREDENTIALS)) ||
                (input.get(Base.InvokeKeys.COMMAND).equals(Authn.InvokeCommands.CREDENTIALS_CHANGE))
            ) {
                doAuth(input, output);
            } else {
                output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.UNSUPPORTED);
                throw new IllegalArgumentException("unsupported:" + input.get(Base.InvokeKeys.COMMAND));
            }
            output.mput(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        } catch (Throwable e) {
            LOG.error(
                "Unexpected Exception invoking: {}",
                input.<ExtUUID>get(Base.InvokeKeys.COMMAND)
            );
            LOG.debug(
                "Exception:",
                e
            );
            output.putIfAbsent(Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED);
            output.put(Base.InvokeKeys.MESSAGE, e.getMessage());
        }
    }

    private void doLoad(ExtMap input) throws Exception {
        input.get(
            Base.InvokeKeys.CONTEXT, ExtMap.class
        ).mput(
            Base.ContextKeys.EXTENSION_NAME,
            Config.PACKAGE_NAME.concat(".authn")
        ).mput(
            Authn.ContextKeys.CAPABILITIES,
            (
                Authn.Capabilities.AUTHENTICATE_CREDENTIALS |
                Authn.Capabilities.CREDENTIALS_CHANGE |
                Authn.Capabilities.AUTHENTICATE_PASSWORD |
                0
            )
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
    }

    private void doInit(ExtMap input) throws SQLException, IOException {
        DataSource ds = new DataSourceProvider(configuration).provide();
        this.authentication = new Authentication(ds);
        this.tasks = new Tasks(ds, this.authentication);
        ExtensionUtils.checkDbVersion(
            ds,
            input.<ExtMap>get(Base.InvokeKeys.CONTEXT).<String>get(Base.ContextKeys.CONFIGURATION_FILE)
        );
    }

    private void doAuth(ExtMap input, ExtMap output)
        throws GeneralSecurityException, SQLException, IOException {

        Authentication.AuthResponse response = authentication.doAuth(
            input.get(Authn.InvokeKeys.USER, String.class),
            input.get(Authn.InvokeKeys.CREDENTIALS, String.class),
            input.get(Base.InvokeKeys.COMMAND).equals(Authn.InvokeCommands.CREDENTIALS_CHANGE),
            input.get(Authn.InvokeKeys.CREDENTIALS_NEW, String.class)
        );

        output.mput(
            Authn.InvokeKeys.AUTH_RECORD,
            (response.authRecord != null) ?
            new ExtMap().mput(
                Authn.AuthRecord.PRINCIPAL,
                response.authRecord.principal
            ).mput(
                Authn.AuthRecord.VALID_TO,
                new SimpleDateFormat(
                    "yyyyMMddHHmmssZ",
                    Locale.ROOT
                ).format(response.authRecord.validTo)
            ):
            null
        ).mput(
            Authn.InvokeKeys.PRINCIPAL,
            response.principal
        ).mput(
            Authn.InvokeKeys.RESULT,
            response.result
        ).mput(
            Authn.InvokeKeys.USER_MESSAGE,
            response.dailyMsg
        ).mput(
            Base.InvokeKeys.MESSAGE,
            response.baseMsg
        );
    }
}
