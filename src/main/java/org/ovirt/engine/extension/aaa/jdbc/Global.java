package org.ovirt.engine.extension.aaa.jdbc;

import java.util.regex.Pattern;

import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;

public class Global {
    public static final int SETTINGS_SPECIAL = -1;
    public static final String NAMESPACE = "*";

    /** pattern to check user/group names and attributes against in both cli and api. */
    public static Pattern ATTRIBUTE_PATTERN = Pattern.compile("[\\p{Alnum}\\-\\s_]+");
    public static Pattern SEARCH_PATTERN = Pattern.compile("[\\p{Alnum}\\-\\s_*]+");

    public static class SearchContext {
        /** can be principal or group */
        public static final ExtKey IS_PRINCIPAL = new ExtKey("AAA_JDBC_IS_PRINCIPAL", Boolean.class, "c991a616-7526-403c-a6ff-89578ef4351d");
        public static final ExtKey WITH_GROUPS = new ExtKey("AAA_JDBC_WITH_GROUPS", Boolean.class, "2161fc6c-9c83-4865-b331-39189d3858ea");
        public static final ExtKey RECURSIVE = new ExtKey("AAA_JDBC_RECURSIVE", Boolean.class, "3dbd6bcc-64c3-4675-aadb-9afdd6ab6966");
        public static final ExtKey PAGE_SIZE = new ExtKey("AAA_JDBC_PAGE_SIZE", Integer.class, "e0fe28ad-24c0-4d29-8868-5c639701f4b5");
    }

    public static class InvokeKeys {
        /** @see org.ovirt.engine.extension.aaa.jdbc.Global.SearchContext */
        public static final ExtKey SEARCH_CONTEXT = new ExtKey("AAA_JDBC_SEARCH_CONTEXT", ExtMap.class, "bc9b1f3e-6d0e-4ce7-bade-507254815c49");
    }
}
