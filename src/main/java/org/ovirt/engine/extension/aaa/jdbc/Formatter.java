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

package org.ovirt.engine.extension.aaa.jdbc;

import org.apache.commons.lang.RandomStringUtils;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.DataSourceProvider;
import org.slf4j.helpers.MessageFormatter;

public class Formatter {

    public static String format(String messagePattern, Object arg) {
        return MessageFormatter.format(messagePattern, arg).getMessage();
    }

    public static String format(String messagePattern, Object arg1, Object arg2) {
        return MessageFormatter.format(messagePattern, arg1, arg2).getMessage();
    }

    public static String format(String messagePattern, Object... args) {
        return MessageFormatter.arrayFormat(messagePattern, args).getMessage();
    }

    /**
     * Escapes string value using PostreSQL dollar quoting
     */
    public static String escapeString(String value) {
        // use dollar-quoted string values to prevent SQL injection
        String dollarQuote = RandomStringUtils.randomAlphabetic(5);
        return String.format(
            "$%s$%s$%s$",
            dollarQuote,
            value,
            dollarQuote
        );
    }

    /**
     * Converts object to string and escapes value using PostgreSQL dollar quoting
     */
    public static String escapeString(Object value) {
        return value == null ? null : escapeString(value.toString());
    }

    public static String replaceSchemaPlaceholder(String dbObject) {
        return dbObject.replaceAll("@SCHEMA_NAME@", DataSourceProvider.getSchemaName());
    }
}
