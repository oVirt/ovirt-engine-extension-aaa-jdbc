/*
 * Copyright 2012-2015 Red Hat Inc.
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
package org.ovirt.engine.extension.aaa.jdbc.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.ExtUUID;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Schema {
    public static class Entities {
        public static final ExtUUID SETTINGS = new ExtUUID("AAA_JDBC_SETTINGS", "761445ec-fe4e-4e1d-9f12-8ca6d9a66a23");
        /**
         * Authn view of a user.
         * @see Schema.User
         * */
        public static final ExtUUID USER = new ExtUUID("AAA_JDBC_USER", "74cead40-a5f4-41b2-b74d-c0f961a6e66e");
        /** Group  */
        public static final ExtUUID GROUP = new ExtUUID();
        /** A cursor for a SEARCH_PAGE */
        public static final ExtUUID CURSOR = new ExtUUID("AAA_JDBC_CURSOR", "ce383ef3-1f3f-41c2-95e0-d9cc4ec5fbb6");
        /**
         * Authz view of a collection of Principals or Groups depending on cursor used to obtain page.
         * Contains information from (users, user_attributes, user_groups) OR
         * (groups, group_attributes and group_groups). Must first acquire a cursor.
         */
        public static final ExtUUID SEARCH_PAGE = new ExtUUID("AAA_JDBC_ENTITIES_PAGE", "fe1d2d6c-0595-4eab-b243-feec4baacf13");
    }

    public static class InvokeKeys {
        /** @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.Entities */
        public static final ExtKey ENTITY = new ExtKey("AAA_JDBC_CLI_ENTITY", ExtUUID.class,"40cdb194-8393-4930-9906-b356cfedf6d1");
        /**
         *
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.UserKeys
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.UserIdentifiers
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.GroupKeys
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.GroupIdentifiers
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.CursorKeys
         * @see org.ovirt.engine.extension.aaa.jdbc.core.Schema.Settings
         */
        public static final ExtKey ENTITY_KEYS = new ExtKey("AAA_JDBC_ENTITY_KEYS", ExtMap.class,"632b76ad-aac4-4c97-b3c5-7b1ffa707a21");

        /** @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.ModificationTypes */
        public static final ExtKey MODIFICATION_TYPE = new ExtKey("AAA_JDBC_MODIFICATION_TYPE", Integer.class, "c321aedb-ae00-4ec8-a478-32426da7d4d2");
        public static final ExtKey USER_RESULT = new ExtKey("AAA_JDBC_USER_RESULT", User.class, "4012d9b1-735a-43eb-9bc1-c065c9638d70");
        public static final ExtKey SETTINGS_RESULT = new ExtKey("AAA_JDBC_SETTINGS_RESULT", ExtMap.class, "966f32e0-7154-4736-8890-a05a694a9cd0");
        public static final ExtKey CURSOR_RESULT = new ExtKey("AAA_JDBC_CURSOR_RESULT", Sql.Cursor.class, "016aaa8b-8ae2-4220-a277-8197e4eb5405");
        /** SEARCH_PAGE_RESULT is null if there are no results */
        public static final ExtKey SEARCH_PAGE_RESULT = new ExtKey("AAA_JDBC_PRINCIPAL_RESULT", Collection /**<ExtMap.class*/.class, "7a5c3151-1160-4668-b34c-0062997fc7af");
        public static final ExtKey DATA_SOURCE = new ExtKey("AAA_JDBC_DATA_SOURCE", DataSource.class, "80564c29-c9cf-499d-ba7f-c0db66f426e7");
    }

    public static class SharedKeys {
        public static final ExtKey ATTRIBUTE_NAME = new ExtKey("CATALOG_ATTRIBUTE_NAME", String.class, "d4396c43-74f1-44a3-9bd6-d5afb3b6bceb");
        public static final ExtKey ATTRIBUTE_VALUE = new ExtKey("CATALOG_ATTRIBUTE_VALUE", String.class, "17ac4a7f-cea6-453e-8b38-b1ed218984d3");

        public static final ExtKey ADD_GROUP = new ExtKey("CATALOG_ADD_GROUP", String.class, "60cfb4ad-5f23-4feb-9fe5-bda4d1a92aef");
        public static final ExtKey REMOVE_GROUP = new ExtKey("CATALOG_REMOVE_GROUP", String.class, "718b4a9b-8dfb-4537-aa4e-d96ed36e03cc");
        public static final ExtKey ATTRIBUTES = new ExtKey("CATALOG_USER_ATTRIBUTES", Collection/*<ExtMap>*/.class, "c1e68323-dc2d-47c3-b9aa-a830c5ef3eec");
    }

    public static class UserIdentifiers {
        /** one of these must be provided. */
        public static final ExtKey USERNAME = new ExtKey("CATALOG_USERNAME", String.class, "4379174b-00ca-4123-98c3-e7e6956756fc");
        public static final ExtKey USER_ID = new ExtKey("CATALOG_ID", Integer.class, "7e067ee4-49c0-4ece-99ae-51dcd75e8988");
    }
    public static class UserKeys {
        public static final ExtKey UUID = new ExtKey("CATALOG_EXTERNAL_ID", String.class, "328c1fdd-fe5d-40c3-b861-c4287b8c9ea2");
        public static final ExtKey NEW_USERNAME = new ExtKey("CATALOG_NEW_USERNAME", String.class, "03c43642-093c-473d-906f-9a0453617169");
        /** change password if complexity and password history allow, insert a user_password_history record */
        public static final ExtKey PASSWORD = new ExtKey("CATALOG_PASSWORD",  String.class, "22307c2e-c7f2-4339-b392-8f3086d4c737", ExtKey.Flags.SENSITIVE);
        /** needed for pass change */
        public static final ExtKey OLD_PASSWORD = new ExtKey("CATALOG_PLD_PASSWORD",  String.class, "6b69d8c8-5a7e-44fb-83ae-a059ba10d5d6", ExtKey.Flags.SENSITIVE);
        public static final ExtKey PASSWORD_VALID_TO = new ExtKey("CATALOG_PASSWORD_VALID_TO", Long.class, "0997d4e7-13d9-4a86-85d4-e9499d8de56e");
        public static final ExtKey VALID_FROM = new ExtKey("CATALOG_VALID_FROM", Long.class, "5bae23fe-b661-48fe-ad6d-321c50542857");
        public static final ExtKey VALID_TO = new ExtKey("CATALOG_VALID_TO", Long.class, "5a7514b0-c670-46cf-8822-c0aedd8ccce8");
        public static final ExtKey LOGIN_ALLOWED = new ExtKey("CATALOG_LOGIN_ALLOWED", String.class, "1e932817-f863-4e49-bb44-a823be9ef4fe");
        public static final ExtKey NOPASS = new ExtKey("CATALOG_NOPASSWD", Boolean.class, "dc85f1d8-0933-4f15-b037-ef4007229436");
        public static final ExtKey FORCE_PASSWORD = new ExtKey("CATALOG_FORCE_PASSWORD", Boolean.class, "9b187ad5-b403-4412-bf68-debc7fcc17a6");
        public static final ExtKey DISABLED = new ExtKey("CATALOG_DISABLED", Boolean.class, "7a5d77c6-f831-400b-bc8e-b9d8ea286408");
        /** unlock_time = value,  consecutive_failures = 0 */
        public static final ExtKey UNLOCK_TIME = new ExtKey("CATALOG_UNLOCK_TIME", Long.class, "7b1042a6-35ea-4108-8cc4-2ed142ba002f");
        /**  last_successful_login = value, consecutive_failures = 0 */
        public static final ExtKey SUCCESSFUL_LOGIN = new ExtKey("CATALOG_SUCCESSFUL_LOGIN", Long.class, "338117a2-4eec-4aca-9dd1-9bcbe95600f2");
        /**  last_unsuccessful_login = value, consecutive_failures++, insert a failed_login record */
        public static final ExtKey UNSUCCESSFUL_LOGIN = new ExtKey("CATALOG_SUCCESSFUL_LOGIN", Long.class, "c10a40f8-5fed-4908-a27e-ec9b0ae83324");
    }

    public static class GroupIdentifiers {
        public static final ExtKey NAME = new ExtKey("CATALOG_GROUP_NAME", String.class, "6e3c6b25-4999-4602-9ce8-4e1c278ecb3b");
    }

    public static class GroupKeys {
        /** Database ID of the group. **/
        public static final ExtKey DB_ID = new ExtKey("CATALOG_GROUP_DB_ID", Integer.class, "30f9b8e8-66bd-4744-ba56-b1457e38d214");
        public static final ExtKey UUID = new ExtKey("CATALOG_GROUP_ID", String.class, "6cb2a99b-5597-4335-8a88-9cd4309f778d");
        public static final ExtKey NEW_NAME = new ExtKey("CATALOG_GROUP_NEW_NAME", String.class, "81c2bbfb-189a-485d-8330-dd46cfd14f2e");
    }

    public static class CursorKeys {
        /** needed to create a cursor */
        public static final ExtKey FILTER = new ExtKey("AAA_JDBC_FILTER", String.class, "632b76ad-aac4-4c97-b3c5-7b1ffa707a21");
    }

    /** Note: names of loaded keys are retrieved from the database. */
    public static class Settings {
        /**
         * Authentication related
         */
        public static final ExtKey PASSWORD_HISTORY_LIMIT = new ExtKey("", Integer.class, "e843bc2a-0878-4b6f-9be3-32e83169fb7c");
        public static final ExtKey LOCK_MINUTES = new ExtKey("", Integer.class, "78b5138a-d52b-464d-a2a7-5fed55bdf7b3");
        public static final ExtKey PRESENT_WELCOME_MESSAGE = new ExtKey("", Boolean.class, "0ae5affd-15e5-4bb1-9910-f091b64b7197");
        public static final ExtKey MESSAGE_SEPARATOR = new ExtKey("", String.class, "ecf6d62a-10f8-4fad-b401-75c9d0788955");
        public static final ExtKey MESSAGE_OF_THE_DAY = new ExtKey("", String.class, "df496823-6814-4720-a302-c723f629f847");
        public static final ExtKey PASSWORD_EXPIRATION_DAYS = new ExtKey("", Integer.class, "dc3e2fb4-cbcc-4f5c-9b06-5db9ec534aa8");
        public static final ExtKey PASSWORD_EXPIRATION_NOTICE_DAYS = new ExtKey("", Integer.class, "69d5cec2-bd1a-42e1-84f0-05627ee476b3");
        public static final ExtKey MAX_FAILURES_PER_MINUTE = new ExtKey("", Integer.class, "d9fce842-1906-40c0-b8d0-f01d2454623b");
        public static final ExtKey MAX_LOGIN_MINUTES = new ExtKey("", Integer.class, "bb90c43c-45cb-4af9-8c3e-4f6dee6ba60b");
        public static final ExtKey MINIMUM_RESPONSE_SECONDS = new ExtKey("", Integer.class, "1a496c2f-02d7-4057-bfeb-23019d3941ae");
        public static final ExtKey MAX_FAILURES_SINCE_SUCCESS = new ExtKey("", Integer.class, "d03f5b67-5dca-4730-b1ac-01a6e8f31f25");
        public static final ExtKey MAX_FAILURES_PER_INTERVAL = new ExtKey("", Integer.class, "3b1e221c-21d2-45c9-95c5-2f71deec3b9f");
        public static final ExtKey INTERVAL_HOURS = new ExtKey("", Integer.class, "756f2a73-be20-4103-b82f-f41dae35f8bf");
        public static final ExtKey ALLOW_EXPIRED_PASSWORD_CHANGE = new ExtKey("", Boolean.class, "aaa93f69-7b75-44ee-b8a7-4d4736e73be1");
        public static final ExtKey PASSWORD_COMPLEXITY = new ExtKey("", String.class, "b55243d1-27b5-49bf-8436-67d5ada33975");
        public static final ExtKey PBE_ALGORITHM = new ExtKey("", String.class, "8669297e-cfd7-45e3-80cd-a464c90694d7");
        public static final ExtKey PBE_KEY_SIZE = new ExtKey("", Integer.class, "8aa785db-bd8e-4ddd-9590-185b8a870f6f");
        public static final ExtKey PBE_ITERATIONS = new ExtKey("", Integer.class, "fa528912-4f3a-4c3a-8e93-561e92d785e8");
        public static final ExtKey MIN_LENGTH = new ExtKey("", Integer.class, "24e7de2f-a714-4f3e-8f64-13bc6ee7525b");
        /**
         * Authorization keys
         */
        public static final ExtKey MAX_PAGE_SIZE  = new ExtKey("", Integer.class, "8a077fb6-7271-4d79-a0b5-aa9a84384b69");
        /**
         * Tasks keys
         */
        public static final ExtKey REFRESH_SETTINGS_INTERVAL_MINUTES = new ExtKey("", Long.class, "fba813af-4c30-448f-8df9-9334449c149e");
        public static final ExtKey HOUSE_KEEPING_INTERVAL_HOURS = new ExtKey("", Long.class, "d89abf58-f0ca-4be8-8ec3-45a1f3645c7b");
        public static final ExtKey FAILED_LOGINS_OLD_DAYS  = new ExtKey("", Integer.class, "12fb6fee-797b-47e7-83e1-1f1fd8b8dd05");
        /**
         * Meta
         */
        public static final ExtKey SETTING_DESCRIPTIONS  = new ExtKey("SETTINGS_DESCRIPTIONS", ExtMap.class, "4e085d97-53d9-4ba8-9303-6df3b060e580");
    }

    public static class AuthzInternal {
        public static final ExtKey USER_DESCRIPTION = new ExtKey("AAA_JDBC_USER_DESCRIPTION", String.class, "5b1d284d-1688-403b-9e0a-af856961532c");
        public static final ExtKey GROUP_DESCRIPTION = new ExtKey("AAA_JDBC_GROUP_DESCRIPTION", String.class, "e13dcd41-dcf7-483b-9cac-5473c6f9d8e8");
        public static final Integer LIKE = 1000;
    }

    private static class SettingsResolver implements Sql.ResultsResolver<ExtMap> {
        @Override
        public ExtMap resolve(ResultSet rs, ExtMap ctx) throws SQLException {
            ExtMap settings = new ExtMap();
            ExtMap descriptions = new ExtMap();
            UUID uuid;
            ExtKey key = null;
            String value = null;
            try {
                while (rs.next()) {
                    uuid = UUID.fromString(rs.getString("uuid"));
                    key = SETTING_KEYS.get(uuid);
                    value = rs.getString("value");
                    if (key != null) {
                        settings.put(
                            new ExtKey(rs.getString("name"), key.getType(), key.getUuid().getUuid().toString()),
                            key.getType().getConstructor(java.lang.String.class).newInstance(value)
                        );
                        descriptions.put(
                            new ExtKey(rs.getString("name"), String.class, key.getUuid().getUuid().toString()),
                            rs.getString("description")
                        );
                    } else {
                        throw new RuntimeException("key for " + uuid + " not found");
                    }
                }
            } catch ( NoSuchMethodException|InvocationTargetException|InstantiationException|IllegalAccessException e) {
                throw new RuntimeException(
                    Formatter.format(
                        "Could not convert setting to expected type. value: {} requested type: {}",
                        value,
                        key.getType()
                    ),
                    e
                );
            }
            return settings.mput(Settings.SETTING_DESCRIPTIONS, descriptions);
        }
    }

    public static class
        searchPageResolver implements Sql.ResultsResolver<Collection<ExtMap>> {
        @Override
        public Collection<ExtMap> resolve(ResultSet rs, ExtMap context) throws SQLException {
            Collection<ExtMap> page = null;
            ExtMap next;
            do {
                next = nextPage(rs, context);
                if (next != null) {
                    if (page == null) {
                        page = new ArrayList<>();
                    }
                    page.add(next);
                }
            } while (next != null && page.size() < context.get(Global.SearchContext.PAGE_SIZE, Integer.class));
            return page;
        }

        private static ExtMap nextPage(ResultSet rs, ExtMap context) throws SQLException {
            String entityId = null;
            Boolean principal = context.get(Global.SearchContext.IS_PRINCIPAL, Boolean.class);
            String idName = principal ? "user_id" : "group_id";
            boolean reachedNext = false;
            ExtMap page = null;
            while (!reachedNext && rs.next()) {
                if (entityId == null || entityId.equals(rs.getString(idName))) { // same entity, add row!
                    if (page == null){
                        page = new ExtMap();
                    }
                    if (principal) {
                        putNextPrincipal(rs, page, context);
                    } else {
                        putNextGroup(rs, page, context);
                    }
                    entityId = rs.getString(idName);
                } else {
                    reachedNext = true;
                    rs.relative(-1);
                }
             }
            return page;
        }
    }

    public static void putNextPrincipal(
        ResultSet rs,
        ExtMap next,
        ExtMap context
    ) throws SQLException {
        if (!next.containsKey(Authz.PrincipalRecord.NAMESPACE)) {
            next.put(Authz.PrincipalRecord.NAMESPACE, rs.getString("namespace"));
            next.put(Authz.PrincipalRecord.ID, rs.getString("user_uuid"));
            next.put(Authz.PrincipalRecord.NAME, rs.getString("user_name"));
            next.put(Authz.PrincipalRecord.PRINCIPAL, rs.getString("user_name"));
            next.put(Authz.PrincipalRecord.DISPLAY_NAME, rs.getString("user_display_name"));
            next.putIfAbsent(Authz.PrincipalRecord.DISPLAY_NAME, "");
            next.put(AuthzInternal.USER_DESCRIPTION, rs.getString("user_description"));
            next.putIfAbsent(AuthzInternal.USER_DESCRIPTION, "");
            next.put(Authz.PrincipalRecord.EMAIL, rs.getString("user_email"));
            next.putIfAbsent(Authz.PrincipalRecord.EMAIL, "");
            next.put(Authz.PrincipalRecord.FIRST_NAME, rs.getString("user_first_name"));
            next.putIfAbsent(Authz.PrincipalRecord.FIRST_NAME, "");
            next.put(Authz.PrincipalRecord.LAST_NAME, rs.getString("user_last_name"));
            next.putIfAbsent(Authz.PrincipalRecord.LAST_NAME, "");
            next.put(Authz.PrincipalRecord.DEPARTMENT, rs.getString("user_department"));
            next.putIfAbsent(Authz.PrincipalRecord.DEPARTMENT, "");
            next.put(Authz.PrincipalRecord.TITLE, rs.getString("user_title"));
            next.putIfAbsent(Authz.PrincipalRecord.TITLE, "");
            next.put(Authz.PrincipalRecord.GROUPS, new ArrayList<ExtMap>());
            if (context.get(Global.SearchContext.ALL_ATTRIBUTES, Boolean.class, false)) {
                next.put(UserKeys.DISABLED, rs.getBoolean("user_disabled"));
                next.put(UserKeys.UNLOCK_TIME, rs.getTimestamp("user_unlock_time").getTime());
                next.put(UserKeys.VALID_FROM, rs.getTimestamp("user_valid_from").getTime());
                next.put(UserKeys.VALID_TO, rs.getTimestamp("user_valid_to").getTime());
                next.put(UserKeys.NOPASS, rs.getBoolean("user_nopasswd"));
                next.put(UserKeys.SUCCESSFUL_LOGIN, rs.getTimestamp("user_last_successful_login").getTime());
                next.put(UserKeys.UNSUCCESSFUL_LOGIN, rs.getTimestamp("user_last_unsuccessful_login").getTime());
                next.put(UserKeys.PASSWORD_VALID_TO, rs.getTimestamp("user_password_valid_to").getTime());
            }
        }

        if (context.get(Global.SearchContext.WITH_GROUPS, Boolean.class)) {
            String groupId = rs.getString("user_group_id");
            if (groupId != null) {
                ExtMap group = new ExtMap();
                group.put(Authz.GroupRecord.NAMESPACE, rs.getString("namespace"));
                group.put(Authz.GroupRecord.ID, rs.getString("user_group_uuid"));
                group.put(Authz.GroupRecord.NAME, rs.getString("user_group_name"));
                group.put(Authz.GroupRecord.DISPLAY_NAME, rs.getString("user_group_display_name"));
                group.putIfAbsent(Authz.GroupRecord.DISPLAY_NAME, "");
                group.put(AuthzInternal.GROUP_DESCRIPTION, rs.getString("user_group_description"));
                group.putIfAbsent(AuthzInternal.GROUP_DESCRIPTION, "");


                next.get(Authz.PrincipalRecord.GROUPS, ArrayList.class).add(group);
            }
        }
    }

    private static void putNextGroup(
        ResultSet rs,
        ExtMap next,
        ExtMap context
    ) throws SQLException {
        if (!next.containsKey(Authz.GroupRecord.NAMESPACE)) {
            next.put(Authz.GroupRecord.NAMESPACE, rs.getString("namespace"));
            next.put(Authz.GroupRecord.ID, rs.getString("group_uuid"));
            next.put(Authz.GroupRecord.NAME, rs.getString("group_name"));
            next.put(Authz.GroupRecord.DISPLAY_NAME, rs.getString("group_display_name"));
            next.putIfAbsent(Authz.GroupRecord.DISPLAY_NAME, "");
            next.put(AuthzInternal.GROUP_DESCRIPTION, rs.getString("group_description"));
            next.putIfAbsent(AuthzInternal.GROUP_DESCRIPTION, "");
            next.put(Authz.GroupRecord.GROUPS, new ArrayList<ExtMap>());
            if (context.get(Global.SearchContext.ALL_ATTRIBUTES, Boolean.class, false)) {
                next.put(GroupKeys.DB_ID, rs.getInt("group_id"));
            }
        }
        if (context.get(Global.SearchContext.WITH_GROUPS, Boolean.class)) {
            String nested = rs.getString("group_group_id");
            if (nested != null) {
                ExtMap group = new ExtMap();
                group.put(Authz.GroupRecord.NAMESPACE, rs.getString("namespace"));
                group.put(Authz.GroupRecord.ID, rs.getString("group_group_uuid"));
                group.put(Authz.GroupRecord.NAME, rs.getString("group_group_name"));
                group.put(Authz.GroupRecord.DISPLAY_NAME, rs.getString("group_group_display_name"));
                group.putIfAbsent(Authz.GroupRecord.DISPLAY_NAME, "");
                group.put(AuthzInternal.GROUP_DESCRIPTION, rs.getString("group_group_description"));
                group.putIfAbsent(AuthzInternal.GROUP_DESCRIPTION, "");

                next.get(Authz.GroupRecord.GROUPS, ArrayList.class).add(group);
            }
        }
    }


    /**
     * Authn view of a user.
     */
    public static class User {
        private static class UserResolver implements Sql.ResultsResolver<User> {
            @Override
            public User resolve(ResultSet rs, ExtMap context) throws SQLException {
                User user = null;
                if (rs.next()) {
                    user = new User();
                    user.id = rs.getInt("u_id");
                    user.name = rs.getString("u_name");
                    user.password = rs.getString("u_password");
                    user.passwordValidTo = rs.getTimestamp("u_password_valid_to").getTime();
                    user.loginAllowed = rs.getString("u_login_allowed");
                    user.disabled = rs.getBoolean("u_disabled");
                    user.nopasswd = rs.getBoolean("u_nopasswd");
                    user.unlockTime = rs.getTimestamp("u_unlock_time").getTime();
                    user.lastSuccessfulLogin = rs.getTimestamp("u_last_successful_login").getTime();
                    user.lastUnsuccessfulLogin = rs.getTimestamp("u_last_unsuccessful_login").getTime();
                    user.consecutiveFailures = rs.getInt("u_consecutive_failures");
                    user.validFrom = rs.getTimestamp("u_valid_from").getTime();
                    user.validTo = rs.getTimestamp("u_valid_to").getTime();

                    user.failedLogins = new TreeSet<>(new Comparator<FailedLogin>() {

                        @Override
                        public int compare(FailedLogin o1, FailedLogin o2) {
                            return Long.compare(o2.minute, o1.minute);
                        }
                    });

                    user.oldPasswords = new TreeSet<>(new Comparator<PasswordHistory>() {
                        @Override
                        public int compare(PasswordHistory o1, PasswordHistory o2) {
                            return Long.compare(o1.date, o2.date);
                        }
                    });
                    do {
                        FailedLogin fl = FailedLogin.fromResultSet(rs);
                        if (fl != null && !user.failedLogins.contains(fl)) {
                            user.failedLogins.add(fl);
                        }

                        PasswordHistory passwordHistory = PasswordHistory.fromResultSet(rs);

                        if (passwordHistory != null) {
                            user.addOldPassword(
                                passwordHistory,
                                context.get(Settings.PASSWORD_HISTORY_LIMIT, Integer.class)
                            );
                        }

                    } while (rs.next());
                }

                return user;
            }
        }

        public static class PasswordHistory {
            public final String password;
            public final long date;

            public PasswordHistory(String password, long date) {
                this.password = password;
                this.date = date;
            }

            public static PasswordHistory fromResultSet(ResultSet rs) throws SQLException{
                PasswordHistory ret = null;
                if (rs.getString("uph_password") != null) {
                    ret = new PasswordHistory(rs.getString("uph_password"), rs.getTimestamp("uph_changed").getTime());
                }
                return ret;
            }
        }

        public static class FailedLogin {

            public final long minute;

            public final int count;

            private FailedLogin(long minute, int count) {
                this.minute = minute;
                this.count = count;
            }

            public static FailedLogin fromResultSet(ResultSet rs) throws SQLException {
                FailedLogin ret = null;
                if (rs.getTimestamp("fl_minute_start") != null) {
                    ret = new FailedLogin(rs.getTimestamp("fl_minute_start").getTime(), rs.getInt("fl_count"));
                }
                return ret;
            }

            @Override
            public String toString() {
                return "FailedLogin{" +
                    "minute=" + minute +
                    ", count=" + count +
                    '}';
            }
        }

        public static final char NOT_ALLOWED = '0';
        private static final int MILLIS_IN_HALF_HOUR = 1000 * 60 * 30;

        private Integer id;
        private String name;
        private String password;
        private long passwordValidTo;
        /**
         * A 336 long string of 0|1 for each half hour during the week.
         * 1 if the user is allowed activity, 0 otherwise.
         * See WEEK_START_SUNDAY.
         */
        private String loginAllowed;
        private boolean disabled;
        private boolean nopasswd;
        private long unlockTime;
        private long lastSuccessfulLogin;
        private long lastUnsuccessfulLogin;
        private int consecutiveFailures;
        private long validFrom;
        private long validTo;

        /**
         * Descending ordered by date.
         */
        private SortedSet<FailedLogin> failedLogins;
        private SortedSet<PasswordHistory> oldPasswords;

        public User() {

        }

        public Integer getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getPassword() {
            return password;
        }

        public long getPasswordValidTo() {
            return passwordValidTo;
        }

        public String getLoginAllowed() {
            return loginAllowed;
        }

        public boolean isNopasswd() {
            return nopasswd;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public long getUnlockTime() {
            return unlockTime;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public long getValidTo() {
            return validTo;
        }

        public long getValidFrom() {
            return validFrom;
        }

        public void addOldPassword(PasswordHistory passwordHistory, int passwordHistoryLimit) {
            oldPasswords.add(passwordHistory);
            if (oldPasswords.size() >= passwordHistoryLimit) {
                oldPasswords.remove(oldPasswords.first());
            }
        }

        public List<PasswordHistory> getOldPasswords() {
            return new LinkedList<>(oldPasswords);
        }

        public int countFailuresSince(long date) {
            int count = 0;
            for (FailedLogin next : failedLogins) {
                if (next.minute >= date) {
                    count += next.count;
                } else {
                    break;
                }
            }
            return count;
        }

        @Override
        public String toString() {
            return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", password='" + password + '\'' +
                ", passwordValidTo=" + passwordValidTo +
                ", loginAllowed='" + loginAllowed + '\'' +
                ", disabled=" + disabled + '\'' +
                ", nopasswd=" + nopasswd + '\'' +
                ", unlockTime=" + unlockTime +
                ", lastSuccessfulLogin=" + lastSuccessfulLogin +
                ", lastUnsuccessfulLogin=" + lastUnsuccessfulLogin +
                ", consecutiveFailures=" + consecutiveFailures +
                ", validFrom=" + validFrom +
                ", validTo=" + validTo +
                ", oldPasswords=" + oldPasswords +
                ", failedLogins=" + failedLogins +
                '}';
        }

        public String getWelcomeMessage() {
            return MessageFormat.format(
                "Last successful: {1}, last failed: {2}, consecutive failures: {3}.",
                DateUtils.toISO(lastSuccessfulLogin),
                DateUtils.toISO(lastUnsuccessfulLogin),
                consecutiveFailures
            );

        }

        public String getExpirationMessage(long loginTime, Integer noticeDays) {
            String ret = "";
            if (DateUtils.add(loginTime, Calendar.DAY_OF_MONTH, noticeDays) >  passwordValidTo) {
                ret = Formatter.format("Password will expire at {}.", DateUtils.toISO(passwordValidTo));
            }
            return ret;
        }

        /**
         * Extract date from login allowed string
         *
         * @param loginTime calculate return date base on login at loginTime.
         * @param loginAllowed the string representing a user's time.
         * @return the max date allowed for loginAllowed based on login time.
         *         return loginTime if login is not allowed.
         */
        public static long getLoginAllowed(long loginTime, String loginAllowed) {
            long ret = Long.MAX_VALUE;
            long interval = loginTime - DateUtils.getLastSunday(loginTime);
            int halfHours = (int) interval / MILLIS_IN_HALF_HOUR;
            String startNow = loginAllowed.substring(halfHours) + loginAllowed.substring(0, halfHours);
            int firstDenial = startNow.indexOf(NOT_ALLOWED);
            if (firstDenial != -1) {
                ret = DateUtils.add(loginTime, Calendar.MINUTE, firstDenial * 30);
            }
            return ret;
        }
    }
    private static final Logger LOG = LoggerFactory.getLogger(Schema.class);

    /** Queries */
    private static final String GET_SETTINGS;
    private static final String GET_USER;
    private static final String SEARCH_PRINCIPAL;
    private static final String SEARCH_GROUP;
    /** SELECT field names for queries exposed for Authz.QueryFilterRecord.KEY */
    public static final Map<ExtKey, String> SEARCH_KEYS = new HashMap<>();
    public static final Map<Integer, String> OPERATORS = new HashMap<>();

    private static final Map<UUID, ExtKey> SETTING_KEYS = new HashMap<>();

    private static final Sql.ResultsResolver<ExtMap> SETTINGS_RESOLVER = new SettingsResolver();
    private static final Sql.ResultsResolver<User> USER_RESOLVER = new User.UserResolver();
    private static final Sql.ResultsResolver<Collection<ExtMap>> ENTITIES_RESOLVER = new searchPageResolver();

    static {
        for (ExtKey key: Arrays.asList(
            Settings.PASSWORD_HISTORY_LIMIT,
            Settings.LOCK_MINUTES,
            Settings.PRESENT_WELCOME_MESSAGE,
            Settings.MESSAGE_SEPARATOR,
            Settings.MESSAGE_OF_THE_DAY,
            Settings.PASSWORD_EXPIRATION_NOTICE_DAYS,
            Settings.PASSWORD_EXPIRATION_DAYS,
            Settings.MAX_FAILURES_PER_MINUTE,
            Settings.MAX_LOGIN_MINUTES,
            Settings.MINIMUM_RESPONSE_SECONDS,
            Settings.MAX_FAILURES_SINCE_SUCCESS,
            Settings.MAX_FAILURES_PER_INTERVAL,
            Settings.INTERVAL_HOURS,
            Settings.ALLOW_EXPIRED_PASSWORD_CHANGE,
            Settings.PASSWORD_COMPLEXITY,
            Settings.PBE_ALGORITHM,
            Settings.PBE_KEY_SIZE,
            Settings.PBE_ITERATIONS,
            Settings.MIN_LENGTH,
            Settings.MAX_PAGE_SIZE ,
            Settings.REFRESH_SETTINGS_INTERVAL_MINUTES,
            Settings.HOUSE_KEEPING_INTERVAL_HOURS,
            Settings.FAILED_LOGINS_OLD_DAYS
        ) ) {
            SETTING_KEYS.put(key.getUuid().getUuid(), key);
        }
        SEARCH_KEYS.put(Authz.PrincipalRecord.NAMESPACE, "namespace");
        SEARCH_KEYS.put(Authz.PrincipalRecord.ID, "u.uuid");
        SEARCH_KEYS.put(Authz.PrincipalRecord.NAME, "u.name");
        SEARCH_KEYS.put(Authz.PrincipalRecord.PRINCIPAL, "u.name");
        SEARCH_KEYS.put(Authz.PrincipalRecord.DISPLAY_NAME, "ua_dn.value");
        SEARCH_KEYS.put(AuthzInternal.USER_DESCRIPTION, "ua_ds.value");
        SEARCH_KEYS.put(Authz.PrincipalRecord.EMAIL, "ua_em.value");
        SEARCH_KEYS.put(Authz.PrincipalRecord.FIRST_NAME, "ua_fn.value");
        SEARCH_KEYS.put(Authz.PrincipalRecord.LAST_NAME, "ua_ln.value");
        SEARCH_KEYS.put(Authz.PrincipalRecord.DEPARTMENT, "ua_dp.value");
        SEARCH_KEYS.put(Authz.PrincipalRecord.TITLE, "ua_tl.value");

        SEARCH_KEYS.put(Authz.GroupRecord.NAMESPACE, "namespace");
        SEARCH_KEYS.put(Authz.GroupRecord.ID, "g.uuid");
        SEARCH_KEYS.put(Authz.GroupRecord.NAME, "g.name");
        SEARCH_KEYS.put(Authz.GroupRecord.DISPLAY_NAME, "ga_dn.value");
        SEARCH_KEYS.put(AuthzInternal.GROUP_DESCRIPTION, "ga_ds.value");

        OPERATORS.put(Authz.QueryFilterOperator.AND, "AND");
        OPERATORS.put(Authz.QueryFilterOperator.OR, "OR");
        OPERATORS.put(Authz.QueryFilterOperator.NOT, "!");
        OPERATORS.put(Authz.QueryFilterOperator.GE, ">=");
        OPERATORS.put(Authz.QueryFilterOperator.LE, "<=");
        OPERATORS.put(Authz.QueryFilterOperator.EQ, "=");
        OPERATORS.put(AuthzInternal.LIKE, "like");


        try (
            InputStream is = Schema.class.getResourceAsStream("sql-statments.properties");
            Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"))
        ) {
            Properties p = new Properties();
            p.load(reader);
            GET_SETTINGS = p.getProperty("catalog.settings.get");
            GET_USER = p.getProperty("authentication.user.get");
            SEARCH_PRINCIPAL = p.getProperty("authorization.principal.search");
            SEARCH_GROUP = p.getProperty("authorization.group.search");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ExtMap get(ExtMap input) throws SQLException {
        ExtMap ret = new ExtMap();
        DataSource ds = input.get(InvokeKeys.DATA_SOURCE, DataSource.class);
        if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.USER)) {
            ret.mput(
                InvokeKeys.USER_RESULT,
                new Sql.Query(
                    Formatter.format(
                        GET_USER,
                        generateWhere(input.get(InvokeKeys.ENTITY_KEYS, ExtMap.class))
                    )
                ).asResults(
                    ds.getConnection(),
                    USER_RESOLVER,
                    input.get(InvokeKeys.SETTINGS_RESULT, ExtMap.class)
                )
            );
        } else if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.SETTINGS)) {
            ret.mput(
                InvokeKeys.SETTINGS_RESULT,
                new Sql.Query(GET_SETTINGS).asResults(
                    ds,
                    SETTINGS_RESOLVER
                )
            );
        } else if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.CURSOR)) {
            ExtMap searchContext = input.get(Global.InvokeKeys.SEARCH_CONTEXT, ExtMap.class);

            ret.mput(
                InvokeKeys.CURSOR_RESULT,
                new Sql.Query(
                    Formatter.format(
                        searchContext.get(Global.SearchContext.IS_PRINCIPAL, Boolean.class) ?
                        SEARCH_PRINCIPAL :
                        SEARCH_GROUP,
                        input.get(InvokeKeys.ENTITY_KEYS, ExtMap.class).get(CursorKeys.FILTER, String.class)
                    )
                ).asCursor(
                    ds.getConnection(),
                    ENTITIES_RESOLVER,
                    true,
                    searchContext
                )
            );
        } else if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.SEARCH_PAGE)) {
            ret.mput(
                InvokeKeys.SEARCH_PAGE_RESULT,
                input.get(InvokeKeys.CURSOR_RESULT, Sql.Cursor.class).resolve(
                    input.get(Global.InvokeKeys.SEARCH_CONTEXT, ExtMap.class)
                )
            );
        } else {
            throw new UnsupportedOperationException();
        }
        return ret;
    }

    public static void modify(ExtMap input) throws SQLException {
        if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.USER)) {
            modifyUser(
                input.get(InvokeKeys.MODIFICATION_TYPE, Integer.class),
                input.get(InvokeKeys.ENTITY_KEYS, ExtMap.class),
                input.get(InvokeKeys.DATA_SOURCE, DataSource.class)
            );
        } else if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.GROUP)) {
            modifyGroup(
                input.get(InvokeKeys.MODIFICATION_TYPE, Integer.class),
                input.get(InvokeKeys.ENTITY_KEYS, ExtMap.class),
                input.get(InvokeKeys.DATA_SOURCE, DataSource.class)
            );
        } else if (input.get(InvokeKeys.ENTITY, ExtUUID.class).equals(Entities.SETTINGS)) {
            modifySettings(
                input.get(InvokeKeys.ENTITY_KEYS, ExtMap.class),
                input.get(InvokeKeys.DATA_SOURCE, DataSource.class)
            );
        } else {
            throw new RuntimeException();
        }

    }

    private static void modifySettings(ExtMap settingKeys, DataSource dataSource) throws SQLException{
        List<String> updates = new ArrayList<>();
        for (Map.Entry<ExtKey, Object> entry: settingKeys.entrySet()) {
            updates.add(
                Formatter.format(
                    "UPDATE settings SET value = {} WHERE uuid = {}",
                    Formatter.escapeString(entry.getValue()),
                    Formatter.escapeString(entry.getKey()
                            .getUuid()
                            .getUuid()
                    )
                )
            );
        }
        new Sql.Modification(updates).execute(dataSource);
    }

    private static void modifyUser(
        int op,
        ExtMap userKeys,
        DataSource ds
    ) throws SQLException {
        Connection conn = null;
        try {
            Integer id = userKeys.get(UserIdentifiers.USER_ID, Integer.class);
            if (id == null) {
                id = new Sql.Query( // id can never change!
                    Formatter.format(
                        "SELECT id from users where name = {} ",
                        Formatter.escapeString(userKeys.get(UserIdentifiers.USERNAME, String.class))
                    )
                ).asInteger(ds, "id");
                if(id == null && op != Sql.ModificationTypes.INSERT) {
                    throw new EntityNotFoundException("User", userKeys.get(UserIdentifiers.USERNAME, String.class));
                }
                if(id != null && op == Sql.ModificationTypes.INSERT) {
                    throw new EntityAlreadyExists("User", userKeys.get(UserIdentifiers.USERNAME, String.class));
                }
            }

            /**
             *  note: setX() and where() are ignored for deletes and insert accordingly.
             *  @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template
             */
            Sql.Template users = new Sql.Template(op, "users");
            if (userKeys.containsKey(UserKeys.UUID)) {
                users.setString("uuid", userKeys.get(UserKeys.UUID, String.class)
                    .toString());
            } else if (op == Sql.ModificationTypes.INSERT) {
                users.setString("uuid", UUID.randomUUID().toString());
            }
            if (userKeys.containsKey(UserKeys.NEW_USERNAME)) {
                users.setString("name", userKeys.get(UserKeys.NEW_USERNAME, String.class));
            }
            if (userKeys.containsKey(UserKeys.PASSWORD)) { // see InsertPassHistoryRecord
                users.setString("password", userKeys.get(UserKeys.PASSWORD, String.class));
            }
            if (userKeys.containsKey(UserKeys.PASSWORD_VALID_TO)) {
                users.setTimestamp("password_valid_to", userKeys.get(UserKeys.PASSWORD_VALID_TO, Long.class));
            }
            if (userKeys.containsKey(UserKeys.VALID_FROM)) {
                users.setTimestamp("valid_from", userKeys.get(UserKeys.VALID_FROM, Long.class));
            }
            if (userKeys.containsKey(UserKeys.VALID_TO)) {
                users.setTimestamp("valid_to", userKeys.get(UserKeys.VALID_TO, Long.class));
            }
            if (userKeys.containsKey(UserKeys.LOGIN_ALLOWED)) {
                users.setString("login_allowed", userKeys.get(UserKeys.LOGIN_ALLOWED, String.class));
            }
            if (userKeys.containsKey(UserKeys.NOPASS)) {
                users.setBool("nopasswd", userKeys.get(UserKeys.NOPASS, Boolean.class));
            }
            if (userKeys.containsKey(UserKeys.DISABLED)) {
                users.setBool("disabled", userKeys.get(UserKeys.DISABLED, Boolean.class));
            }
            if (userKeys.containsKey(UserKeys.UNLOCK_TIME)) {
                users.setTimestamp("unlock_time", userKeys.get(UserKeys.UNLOCK_TIME, Long.class));
            }
            if (userKeys.containsKey(UserKeys.SUCCESSFUL_LOGIN)) {
                users.setTimestamp("last_successful_login", userKeys.get(UserKeys.SUCCESSFUL_LOGIN, Long.class));
            }
            if (userKeys.containsKey(UserKeys.UNLOCK_TIME) ||
                    userKeys.containsKey(UserKeys.SUCCESSFUL_LOGIN)) {
                users.setInteger("consecutive_failures", 0);
            }
            if (userKeys.containsKey(UserKeys.UNSUCCESSFUL_LOGIN)) {
                users.setTimestamp("last_unsuccessful_login", userKeys.get(UserKeys.UNSUCCESSFUL_LOGIN, Long.class));
                if (op != Sql.ModificationTypes.INSERT) {
                    users.setRaw("consecutive_failures", "(consecutive_failures + 1)");
                }
            }
            users.setInteger("id", id);

            conn = ds.getConnection();
            ExtMap ctx = new Sql.Modification( // execute "users" statement
               users.where(
                   generateWhere(userKeys)
               ).asSql()
            ).execute(conn, false);


            if (ctx.get(Sql.ModificationContext.LAST_STATEMENT_ROWS, Integer.class) <= 0) {
                throw new SQLException("ERROR: No rows effected\n");
            }

            if (op == Sql.ModificationTypes.INSERT) {
                id = (Integer) ctx.get(Sql.ModificationContext.GENERATED_IDS, List/**<Integer>*/.class).get(0);
            }
            if (op != Sql.ModificationTypes.DELETE && userKeys.containsKey(SharedKeys.ATTRIBUTES)) { // on delete cascade
                upsertAttribute(id, userKeys.get(SharedKeys.ATTRIBUTES, Collection.class), conn, true);
            }
            if (
                op == Sql.ModificationTypes.UPDATE &&
                userKeys.containsKey(UserKeys.PASSWORD) &&
                !StringUtils.isEmpty(userKeys.get(UserKeys.OLD_PASSWORD, String.class))
            ) {
                InsertPassHistoryRecord(id, userKeys, conn);
            }
            if (op == Sql.ModificationTypes.UPDATE && userKeys.containsKey(UserKeys.UNSUCCESSFUL_LOGIN)) {
                upsertFailedLoginRecord(id, userKeys, conn);
            }
            if (op == Sql.ModificationTypes.UPDATE && userKeys.containsKey(SharedKeys.ADD_GROUP)) {
                updateGroupMembership(id, userKeys.get(SharedKeys.ADD_GROUP, String.class), conn, true, true);
            }
            if (op == Sql.ModificationTypes.UPDATE && userKeys.containsKey(SharedKeys.REMOVE_GROUP)) {
                updateGroupMembership(id, userKeys.get(SharedKeys.REMOVE_GROUP, String.class), conn, false, true);
            }

            conn.commit();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Throwable thr) {
                    LOG.warn("Could not roll back connection, ignoring", thr);
                }
            }
            throw e;
        } finally {
            Sql.closeQuietly(conn);
        }
    }

    private static void modifyGroup(int op, ExtMap groupKeys, DataSource ds) throws SQLException {
        /**
         *  note: setX() and where() are ignored for deletes and insert accordingly.
         *  @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template
         */
        Connection conn = null;
        try {
            Integer id = new Sql.Query( // id can never change!
                Formatter.format(
                    "SELECT id from groups where name = {} ",
                    Formatter.escapeString(groupKeys.get(GroupIdentifiers.NAME, String.class))
                )
            ).asInteger(ds, "id");
            if (id == null && op != Sql.ModificationTypes.INSERT) {
                throw new EntityNotFoundException("Group", groupKeys.get(GroupIdentifiers.NAME, String.class));
            }
            if(id != null && op == Sql.ModificationTypes.INSERT) {
                throw new EntityAlreadyExists("Group", groupKeys.get(GroupIdentifiers.NAME, String.class));
            }


            Sql.Template group = new Sql.Template(op, "groups");
            if (groupKeys.containsKey(GroupKeys.UUID)) {
                group.setString("uuid", groupKeys.get(GroupKeys.UUID, String.class));
            } else if (op == Sql.ModificationTypes.INSERT) {
                group.setString("uuid", UUID.randomUUID().toString());
            }
            if (groupKeys.containsKey(GroupKeys.NEW_NAME) && op == Sql.ModificationTypes.UPDATE) {
                group.setString("name", groupKeys.get(GroupKeys.NEW_NAME, String.class));
            } else {
                group.setString("name", groupKeys.get(GroupIdentifiers.NAME, String.class));
            }
            conn = ds.getConnection();
            ExtMap ctx = new Sql.Modification( // execute "groups" statement
                group.where(
                    Formatter.format(
                        "name = {}",
                        Formatter.escapeString(groupKeys.get(GroupIdentifiers.NAME, String.class))
                    )
                ).asSql()
            ).execute(conn, false);

            if (ctx.get(Sql.ModificationContext.LAST_STATEMENT_ROWS, Integer.class) <= 0) {
                throw new SQLException("ERROR: No rows effected\n");
            }
            if (op == Sql.ModificationTypes.INSERT) {
                id = (Integer) ctx.get(Sql.ModificationContext.GENERATED_IDS, List/**<Integer>*/.class).get(0);
            }
            if (op != Sql.ModificationTypes.DELETE && groupKeys.containsKey(SharedKeys.ATTRIBUTES)) { // on delete cascade

                upsertAttribute(id, groupKeys.get(SharedKeys.ATTRIBUTES, Collection.class), conn, false);
            }
            if (op == Sql.ModificationTypes.UPDATE && groupKeys.containsKey(SharedKeys.ADD_GROUP)) {
                updateGroupMembership(id, groupKeys.get(SharedKeys.ADD_GROUP, String.class), conn, true, false);
            }
            if (op == Sql.ModificationTypes.UPDATE && groupKeys.containsKey(SharedKeys.REMOVE_GROUP)) {
                updateGroupMembership(id, groupKeys.get(SharedKeys.REMOVE_GROUP, String.class), conn, false, false);
            }
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Throwable thr) {
                    LOG.warn("Could not roll back connection", e);
                }
            }
            throw e;
        } finally {
            Sql.closeQuietly(conn);
        }
    }

    private static void updateGroupMembership(
        int memberId,
        String groupName,
        Connection conn,
        boolean add,
        boolean principal
    ) throws SQLException {
        String memberCol = principal ? "user_id" : "group_id";
        Integer groupId = new Sql.Query( // id's can never change
            Formatter.format(
                "SELECT id from groups where name = {}",
                Formatter.escapeString(groupName))
        ).asInteger(conn, "id");
        if (groupId == null) {
            throw new EntityNotFoundException("group",  groupName);
        }

        new Sql.Modification(
            new Sql.Template(
                add?
                Sql.ModificationTypes.INSERT:
                Sql.ModificationTypes.DELETE,
                principal?
                "user_groups":
                "group_groups"
            ).setInteger(
                memberCol,
                memberId
            ).setInteger(
                "in_group_id",
                groupId
            ).where(
                Formatter.format("{} = {} AND {} = {}", memberCol, memberId, "in_group_id", groupId)
            ).asSql()
        ).execute(conn, false);
    }

    private static String generateWhere(ExtMap input) {
        String cond;
        if (input.get(UserIdentifiers.USER_ID) != null) {
            cond =
                Formatter.format(
                    "id = {}",
                    input.get(UserIdentifiers.USER_ID)
                );
        } else if (input.get(UserIdentifiers.USERNAME) != null) {
            cond =
                Formatter.format(
                    "name = {}",
                    Formatter.escapeString(input.get(UserIdentifiers.USERNAME, String.class))
                );
        } else {
            throw new RuntimeException("No user Identifier provided");
        }
        return cond;
    }

    private static void upsertFailedLoginRecord(Integer id, ExtMap input, Connection conn)
        throws SQLException{
        long minuteStart = input.get(UserKeys.UNSUCCESSFUL_LOGIN, Long.class);
        minuteStart -= (minuteStart % (1000 * 60));
        if (
            new Sql.Query(
                Formatter.format(
                    "SELECT count FROM failed_logins WHERE user_id = {} and minute_start = {}",
                    id,
                    DateUtils.toTimestamp(minuteStart)
                )
            ).asInteger(conn, "count") == null
        ) {
            new Sql.Modification(
                new Sql.Template(Sql.ModificationTypes.INSERT, "failed_logins")
                .setInteger("user_id", id)
                .setTimestamp("minute_start", minuteStart)
                .setInteger("count", 1)
                .asSql()
            ).execute(conn, false);
        } else {
            new Sql.Modification(
                new Sql.Template(Sql.ModificationTypes.UPDATE, "failed_logins")
                .setIncrement("count")
                .where(
                    Formatter.format(
                        "user_id = {} AND minute_start = {}",
                        id,
                        DateUtils.toTimestamp(minuteStart)
                    )
                ).asSql()
            ).execute(conn, false);
        }
    }

    private static void InsertPassHistoryRecord(Integer id, ExtMap input, Connection conn)
        throws SQLException {
        new Sql.Modification(
            new Sql.Template(Sql.ModificationTypes.INSERT, "user_password_history")
            .setInteger("user_id", id)
            .setString("password", input.get(UserKeys.OLD_PASSWORD, String.class))
            .setTimestamp("changed", System.currentTimeMillis())
            .asSql()
        ).execute(conn, false);
    }

    private static void upsertAttribute(int id, Collection<ExtMap> attributes, Connection conn, boolean user)
        throws SQLException {
        for (ExtMap attribute: attributes) {
            if (hasAttribute(id, conn, attribute, user)) {
                new Sql.Modification(
                    new Sql.Template(
                        Sql.ModificationTypes.UPDATE,
                        user? "user_attributes": "group_attributes"
                    )
                    .setString("value", attribute.get(SharedKeys.ATTRIBUTE_VALUE, String.class))
                    .where(
                        Formatter.format(
                            "{} = {} AND name = {}",
                            user ? "user_id" : "group_id",
                            id,
                            Formatter.escapeString(attribute.get(SharedKeys.ATTRIBUTE_NAME, String.class))
                        )
                    ).asSql()
                ).execute(conn, false);
            } else {
                new Sql.Modification(
                    new Sql.Template(
                        Sql.ModificationTypes.INSERT,
                        user? "user_attributes": "group_attributes") // always insert first
                        .setString("name", attribute.get(SharedKeys.ATTRIBUTE_NAME, String.class))
                        .setString("value", attribute.get(SharedKeys.ATTRIBUTE_VALUE, String.class))
                        .setInteger(user? "user_id": "group_id", id)
                        .where(Formatter.format(user ? "user_id" : "group_id", id))
                        .asSql()
                ).execute(conn, false); // note: executed in new connection and committed
            }
        }
    }

    private static boolean hasAttribute(int id, Connection conn, ExtMap attribute, boolean user) throws SQLException {
        return new Sql.Query(
            Formatter.format(
                "SELECT value FROM {} WHERE {} = {} and name = {}",
                user ? "user_attributes" : "group_attributes",
                user ? "user_id" : "group_id",
                id,
                Formatter.escapeString(attribute.get(SharedKeys.ATTRIBUTE_NAME, String.class))
            )
        ).asString(conn, "value") != null;
    }

    public static class EntityNotFoundException extends SQLException {
        public EntityNotFoundException(String what, String who) {
            super(new StringBuilder(what).append(" ").append(who).append(" ").append("not found").toString());
        }
    }

    public static class EntityAlreadyExists extends SQLException {
        public EntityAlreadyExists(String what, String who) {
            super(new StringBuilder(what).append(" ").append(who).append(" ").append("already exists").toString());
        }
    }
}
