/*
 * Copyright 2014-2015 Red Hat Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Properties;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.binding.Config;

public class ExtensionUtils {
    public static final ExtMap JDBC_INFO;

    static {
        JDBC_INFO = new ExtMap().mput(
            Base.ContextKeys.AUTHOR,
            "The oVirt Project"
        ).mput(
            Base.ContextKeys.LICENSE,
            "ASL 2.0"
        ).mput(
            Base.ContextKeys.HOME_URL,
            "http://www.ovirt.org"
        ).mput(
            Base.ContextKeys.VERSION,
            Config.PACKAGE_VERSION
        ).mput(
            Base.ContextKeys.EXTENSION_NOTES,
            MessageFormat.format(
                "Display name: {0}",
                Config.PACKAGE_NAME
            )
        ).mput(
            Base.ContextKeys.BUILD_INTERFACE_VERSION,
            Base.INTERFACE_VERSION_CURRENT
        );
    }

    public static Properties loadIncludedConfiguration(ExtMap context) throws IOException {
        try {
            Properties originalConfig =
                context.get(
                    Base.ContextKeys.CONFIGURATION,
                    Properties.class
                );
            Properties expandedConfig = new Properties(originalConfig);

            String baseDir = "/";
            try {
                baseDir = new File(context.get(Base.ContextKeys.CONFIGURATION_FILE, "/dummy")).getParent();
            } catch (Exception ex) {
                // Ignore
            }

            if (originalConfig.containsKey("config.datasource.file")) {
                expandedConfig.load(
                    new InputStreamReader(
                        new FileInputStream(
                            getRelativeFile(baseDir, originalConfig.getProperty("config.datasource.file"))
                        ),
                        Charset.forName("UTF-8")
                    )
                );
            }
            return expandedConfig;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    private static File getRelativeFile(String baseDir, String fileName) {
        File f = new File(fileName);
        if (!f.isAbsolute()) {
            f = new File(baseDir, fileName);
        }
        return f;
    }

}
