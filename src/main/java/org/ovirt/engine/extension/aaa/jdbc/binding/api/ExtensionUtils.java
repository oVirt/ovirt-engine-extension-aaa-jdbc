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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.binding.Config;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;

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
                baseDir = new File(context.get(Base.ContextKeys.CONFIGURATION_FILE, String.class, "/dummy")).getParent();
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

    private static Path getDbScriptsDir(String configurationFile) {
        // config file is located in ${PREFIX}/etc/ovirt-engine/extension.d/${NAME}.properties (for API calls) or
        // in ${PREFIX}/etc/ovirt-engine/aaa/${NAME}.properties (for CLI calls)
        Path prefixDir = Paths.get(configurationFile).normalize().getParent().getParent().getParent().getParent();
        if (prefixDir.getNameCount() == 0) {
            // RPM installation, ${PREFIX} should be /usr instead of /
            prefixDir = Paths.get("/usr");
        }
        // dbscripts are located in ${PREFIX}/share/ovirt-engine-extension-aaa-jdbc/dbscripts/upgrade
        return Paths.get(prefixDir.toString(), "share/ovirt-engine-extension-aaa-jdbc/dbscripts/upgrade");

    }

    private static String getLatestUpgradeScriptName(Path upgradeScriptsDir) throws IOException {
        List<String> fileNames = new ArrayList<>();
        DirectoryStream.Filter<Path> fileFilter = new DirectoryStream.Filter<Path>() {
            public boolean accept(Path file) throws IOException {
                return !Files.isDirectory(file);
            }
        };
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(upgradeScriptsDir, fileFilter)) {
            for (Path path : directoryStream) {
                // in schema_version table script name contains "upgrade/" prefix
                fileNames.add(path.subpath(path.getNameCount() - 2, path.getNameCount()).toString());
            }
        } finally {
        }
        Collections.sort(fileNames);
        return fileNames.get(fileNames.size() -1);
    }

    public static void checkDbVersion(Connection conn, String configurationFile)
    throws IOException, SQLException {
        boolean uptodate = new Sql.Query(
            Formatter.format(
                "SELECT COUNT(script) AS count FROM schema_version WHERE script = {}",
                Formatter.escapeString(
                    getLatestUpgradeScriptName(
                        getDbScriptsDir(configurationFile)
                    )
                )
            )
        ).asInteger(conn, "count") == 1;
        if (!uptodate) {
            throw new RuntimeException(
                "Database schema is older than required by currently installed ovirt-engine-extension-aaa-jdbc " +
                "package version. Please upgrade profile database schema before proceeding (for more info about " +
                "upgrade please take a look at README.admin file contained in ovirt-engine-extension-aaa-jdbc " +
                "package)."
            );
        }
    }
}
