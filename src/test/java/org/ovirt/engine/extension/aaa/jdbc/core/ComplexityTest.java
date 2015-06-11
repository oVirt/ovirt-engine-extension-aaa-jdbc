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
package org.ovirt.engine.extension.aaa.jdbc.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexityTest {

    private static class Test {

        public final Complexity complexity;
        public final String credentials;
        public final boolean checkResult;


        public Test(
            Complexity complexity,
            String credentials,
            boolean checkResult
        ) {
            this.complexity = complexity;
            this.credentials = credentials;
            this.checkResult = checkResult;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ComplexityTest.class);

    public static void main(String[] args) {
        List<Test> tests = Arrays.asList(
            new Test(
                new Complexity(
                    Arrays.asList(
                        new Complexity.ComplexityGroup("", "?", 1),
                        new Complexity.ComplexityGroup("", "@", 1),
                        new Complexity.ComplexityGroup("", "!", 0)
                    )
                ),
                "@re be?ong",
                true
            ),
            new Test(
                new Complexity(
                    Arrays.asList(
                        new Complexity.ComplexityGroup("", "?", 1),
                        new Complexity.ComplexityGroup("", "@", 1),
                        new Complexity.ComplexityGroup("", "!", 0)
                    )
                ),
                "to us!",
                false
            ),

            new Test(
                new Complexity(
                    Arrays.asList(
                        new Complexity.ComplexityGroup("", "abcdefghijklmnopqrstuvwxyz", 3),
                        new Complexity.ComplexityGroup("", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", 2),
                        new Complexity.ComplexityGroup("", "0123456789", 1)
                    )
                ),
                "Al1 Your",
                true
            ),
            new Test(
                new Complexity(
                    Arrays.asList(
                        new Complexity.ComplexityGroup("", "abcdefghijklmnopqrstuvwxyz", 3),
                        new Complexity.ComplexityGroup("", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", 2),
                        new Complexity.ComplexityGroup("", "0123456789", 1)
                    )
                ),
                "bases.",
                false
            ),
            new Test(
                new Complexity(
                    Collections.<Complexity.ComplexityGroup>emptyList()
                ),
                "",
                true
            )

        );
        for (int i = 0; i < tests.size(); i++) {
            Test test = tests.get(i);
            LOG.info(
                "[test {}] complexity: {} credentials: {}",
                i,
                test.complexity,
                test.credentials
            );
            boolean checkResult = test.complexity.check(test.credentials);
            if (checkResult != test.checkResult){
                LOG.error(
                    "expected: {}, received: {}",
                    checkResult,
                    test.checkResult
                );
                System.exit(1);
            }

        }
    }

}
