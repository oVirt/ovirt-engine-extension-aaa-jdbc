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
package org.ovirt.engine.extension.aaa.jdbc.core.datasource;

import java.util.Arrays;
import java.util.List;

import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.core.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTest {

    private static class Test {
        public final int allowAt;
        public final Long loginTime;
        public final boolean isLoginAllowed;

        private Test(int allowAt, Long loginTime, boolean isLoginAllowed) {
            this.allowAt = allowAt;
            this.loginTime = loginTime;
            this.isLoginAllowed = isLoginAllowed;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(UserTest.class);
    public static final String NEVER_ALLOWED = "" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000";

    public void testIsLoginAllowed(List<Test> tests) throws Exception {

        Schema.User user = new Schema.User();
        for (int i=0; i < tests.size(); i++){
            Test test = tests.get(i);
            String loginAllowed = replaceCharAt(NEVER_ALLOWED, test.allowAt, '1');

            LOG.info(
                "[test {}] allowAt: {} loginAllowed: {}, loginTime: {}",
                i,
                test.allowAt,
                loginAllowed,
                test.loginTime
            );
            boolean isLoginAllowed = Schema.User.getLoginAllowed(
                test.loginTime,
                user.getLoginAllowed()
            ) > test.loginTime;
            if (isLoginAllowed != test.isLoginAllowed) {
                LOG.error(
                    "[test {}] expected:{} received: {}",
                    i,
                    test.isLoginAllowed,
                    isLoginAllowed
                );
                System.exit(1);
            } else {
                LOG.error("[test {}] success.", i);
            }
        }

    }

    private String replaceCharAt(String str, int index, char with) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            if (i == index) {
                sb.append(with);
            } else {
                sb.append(str.charAt(i));
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        List<Test> tests = Arrays.asList(
            new Test(
                0,
                DateUtils.fromISO("2014-06-29 00:29:59PST"),
                true
            ),
            new Test(
                72,
                DateUtils.fromISO("2014-06-30 12:05:00PST"),
                true
            ),
            new Test(
                73,
                DateUtils.fromISO("2014-06-30 12:05:00PST"),
                false
            ),
            new Test(
                1,
                DateUtils.fromISO("2014-06-30 12:05:00PST"),
                false
            )
        );
        UserTest ut = new UserTest();
        ut.testIsLoginAllowed(tests);

    }
}
