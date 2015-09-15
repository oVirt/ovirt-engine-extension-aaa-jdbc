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
package org.ovirt.engine.extension.aaa.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.extension.aaa.jdbc.core.Authentication;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.DataSourceProvider;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrationTest {

    private static final String UNLOCK_1 = "UPDATE users SET unlock_time = '1970-01-02 00:00:00' WHERE id = '1';";
    public static final String DELETE_FAILED_LOGINS_1 = "DELETE FROM failed_logins WHERE user_id = 1";
    public static final String ZERO_CONSECUTIVE_FAILURES = "UPDATE users SET consecutive_failures = 0 WHERE id = '1';";

    private static class Task {

        private final String sql;
        private final boolean before;

        public Task(String sql, boolean before) {
            this.sql = sql;
            this.before = before;
        }
    }

    private static class Test {

        public final int method;
        public final String[] args; // subject, credential [, new credentials]
        public final int expected;
        private final String description;
        public final Task[] tasks;

        public Test(
            int method,
            String[] args,
            int expected,
            String description,
            Task... tasks
        ) {
            this.method = method;
            this.args = args;
            this.expected = expected;
            this.description = description;
            this.tasks = tasks;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);
    private static final int AUTHENTICATE_CREDENTIALS = 0;
    private static final int CREDENTIALS_CHANGE = 1;

    private DataSource ds;

    public IntegrationTest(DataSource ds) {
        this.ds = ds;
    }

    private static Properties loadProperties(String name) throws IOException {
        InputStream is = IntegrationTest.class.getResourceAsStream(name);
        Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"));
        Properties p = new Properties();
        p.load(reader);
        return p;
    }

    private static Task before(String prq){
        return new Task(prq, true);
    }

    private static Task after(String prq){
        return new Task(prq, false);
    }

    private void testIntegration(List<Test> tests) throws SQLException, InterruptedException, IOException,
        GeneralSecurityException {
        Authentication authentication = new Authentication(this.ds);


        for (int i = 0; i < tests.size(); i++) {
            Test test = tests.get(i);
            performTasks(test, true);
            LOG.info("[test {}] description: {}", i, test.description);
            Authentication.AuthResponse response = authentication.doAuth(
                test.args[0],
                test.args[1],
                test.method == CREDENTIALS_CHANGE,
                (
                    (test.method == CREDENTIALS_CHANGE) ?
                        test.args[2] :
                        null
                )
            );
            LOG.info(
                "[test {}] response: {}",
                i,
                response.toString()
            );
            performTasks(test, false);
            if (response.result != test.expected) {
                LOG.error("[test {}]  failure. result: {}, expected: {}, ", i, response.result, test.expected);
                System.exit(-1);
            } else {
                LOG.info("[test {}]  success. result: {}", i, response.result);
            }
        }
    }

    private void performTasks(Test test, boolean before) throws SQLException {
        List<String> updates = new LinkedList<>();
        if (test.tasks != null) {
            for (Task task : test.tasks) {
                if (task.before == before) {
                    updates.add(task.sql);
                }
            }
            new Sql.Modification(
                updates
            ).execute(
                this.ds
            );
        }
    }

    public static void main(String[] args)
        throws InterruptedException, SQLException, IOException, GeneralSecurityException {
        DataSource ds = new DataSourceProvider(loadProperties("test-datasource.properties")).provide();

        List<Test> tests = Arrays.asList(
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "brainfreese"},
                Authn.AuthResult.SUCCESS,
                "Should succeed (after delaying response)"
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-nopasswd", ""},
                Authn.AuthResult.SUCCESS,
                "Should succeed - no password",
                // Will make things faster from this point on...
                before("UPDATE settings SET value = '0' WHERE name = 'MINIMUM_RESPONSE_SECONDS' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-nopasswd", ""},
                Authn.AuthResult.SUCCESS,
                "Should succeed - valid to in 5 min",
                before("UPDATE settings SET value = '5' WHERE name = 'MAX_LOGIN_MINUTES' ;"),
                after("UPDATE settings SET value = '-1' WHERE name = 'MAX_LOGIN_MINUTES' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-nopasswd", ""},
                Authn.AuthResult.SUCCESS,
                "Should succeed - welcome message",
                before("UPDATE settings SET value = TRUE WHERE name = 'PRESENT_WELCOME_MESSAGE' ;"),
                after("UPDATE settings SET value = FALSE WHERE name = 'PRESENT_WELCOME_MESSAGE' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "kuku"},
                Authn.AuthResult.CREDENTIALS_INCORRECT,
                "[brute force] Should fail - bad credentials",
                before("UPDATE settings SET value =  '1' WHERE name = 'MAX_FAILURES_PER_MINUTE' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "brainfreese"},
                Authn.AuthResult.GENERAL_ERROR,
                "[brute force] Should fail - attempts per minute",
                after("UPDATE settings SET value =  '20' WHERE name = 'MAX_FAILURES_PER_MINUTE' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "kuku"},
                Authn.AuthResult.CREDENTIALS_INCORRECT,
                "[account lock] Should fail & lock - consecutive failures",
                before("UPDATE settings SET value =  '1' WHERE name = 'MAX_FAILURES_SINCE_SUCCESS' ;")

            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "brainfreese"},
                Authn.AuthResult.ACCOUNT_LOCKED,
                "Should fail - locked",
                after("UPDATE settings SET value =  '5' WHERE name = 'MAX_FAILURES_SINCE_SUCCESS' ;"),
                after(UNLOCK_1)
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "kuku"},
                Authn.AuthResult.CREDENTIALS_INCORRECT,
                "[account lock] Should fail & lock failures per interval",
                before("UPDATE settings SET value =  '1' WHERE name = 'MAX_FAILURES_PER_INTERVAL' ;"),
                before(DELETE_FAILED_LOGINS_1),
                before(ZERO_CONSECUTIVE_FAILURES)
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "brainfreese"},
                Authn.AuthResult.ACCOUNT_LOCKED,
                "[account lock] Should fail - locked: {}",
                after("UPDATE settings SET value =  '20' WHERE name = 'MAX_FAILURES_PER_INTERVAL' ;"),
                after(UNLOCK_1)
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "brainfreese"},
                Authn.AuthResult.SUCCESS,
                "Should succeed & show expiration message",
                before("UPDATE settings SET value =  1 WHERE name = 'PASSWORD_EXPIRATION_NOTICE_DAYS' ;"),
                before(
                    Formatter.format(
                        "UPDATE users SET password_valid_to = '{}' WHERE id = 1 ;",
                        DateUtils.toISO(DateUtils.add(System.currentTimeMillis(), Calendar.HOUR, 1))
                    )
                ),
                after("UPDATE settings SET value =  '0' WHERE name = 'PASSWORD_EXPIRATION_NOTICE_DAYS' ;")
            ),
            new Test(
                CREDENTIALS_CHANGE,
                new String[]{"bob-brainfreese", "brainfreese", "potato"},
                Authn.AuthResult.SUCCESS,
                "Should succeed to change password",
                before("UPDATE settings SET value =  '1' WHERE name = 'PASSWORD_HISTORY_LIMIT' ;")
            ),
            new Test(
                AUTHENTICATE_CREDENTIALS,
                new String[]{"bob-brainfreese", "potato"},
                Authn.AuthResult.SUCCESS,
                "Should succeed - new password"
            ),
            new Test(
                CREDENTIALS_CHANGE,
                new String[]{"bob-brainfreese", "potato", "potato"},
                Authn.AuthResult.GENERAL_ERROR,
                "Should fail - changing into current password"
            ),
            new Test(
                CREDENTIALS_CHANGE,
                new String[]{"bob-brainfreese", "potato", "brainfreese"},
                Authn.AuthResult.GENERAL_ERROR,
                "Should fail - changing into previous password"
            ),
            new Test(
                CREDENTIALS_CHANGE,
                new String[]{"bob-brainfreese", "potato", "brainfreese"},
                Authn.AuthResult.SUCCESS,
                "Should succeed - history limit is 0",
                before("UPDATE settings SET value =  '0' WHERE name = 'PASSWORD_HISTORY_LIMIT' ;"),
                after("UPDATE settings SET value =  '3' WHERE name = 'PASSWORD_HISTORY_LIMIT' ;")
            )
        );

        new IntegrationTest(ds).testIntegration(tests);
    }
}
