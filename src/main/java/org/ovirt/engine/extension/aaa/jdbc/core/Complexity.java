package org.ovirt.engine.extension.aaa.jdbc.core;

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
import java.util.Arrays;
import java.util.List;

public class Complexity {
    public static class ComplexityGroup {
        private final String name;
        private final char[] chars;
        private final int minOccurrences;

        public ComplexityGroup(String name, String chars_, int minOccurrences) {
            this.name = name;
            this.minOccurrences = minOccurrences;
            this.chars = chars_.toCharArray();
        }

        public boolean check(String target) {
            int found = 0;
            for(char chr: chars) {
                for (char tar: target.toCharArray()) {
                    if (chr == tar) {
                        found++;
                    }
                }
            }
            return found >= minOccurrences;
        }

        @Override
        public String toString() {
            return "ComplexityGroup{" +
                "name='" + name + '\'' +
                ", chars=" + Arrays.toString(chars) +
                ", minOccurrences=" + minOccurrences +
                '}';
        }
    }

    private final List<ComplexityGroup> groups;

    public Complexity(List<ComplexityGroup> groups) {
        this.groups = groups;
    }

    public boolean check(String target) {
        boolean ret = true;
        for (ComplexityGroup group: groups) {
            if (!group.check(target)) {
                ret = false;
            }
        }
        return ret;
    }

    public String getUsage() {
        String ret = "";
        for (ComplexityGroup group: groups) {
            ret += String.format("%s \n", group.toString());
        }
        return ret;
    }
}
