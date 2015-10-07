package org.ovirt.engine.extension.aaa.jdbc.binding.cli;

import java.util.Collection;
import java.util.List;

import org.ovirt.engine.api.extensions.ExtKey;

public class ContextKeys {
    public static final ExtKey LOGGING_STARTED = new ExtKey("AAA_JDBC_CLI_LOG_SETUP", Boolean.class, "038a76ae-6845-4f3a-a228-8373970b0ea0");
    public static final ExtKey EXIT_STATUS = new ExtKey("AAA_JDBC_CLI_EXIT_STATUS", Integer.class, "803cc6b3-e1de-45e2-9173-69436cfa7cb7");

    /** ERR_MESSAGES and OUT_MESSAGES are displayed before exit regardless of exit status */
    public static final ExtKey ERR_MESSAGES = new ExtKey("AAA_JDBC_CLI_ERROR_MESSAGES", List/**<String.class>*/.class, "b8b7aed5-2e51-4d4f-a09e-26f9aa7d65ce");
    public static final ExtKey OUT_MESSAGES = new ExtKey("AAA_JDBC_CLI_OUTPUT_MESSAGE", List/**<String.class>*/.class, "7a401ce3-e4d9-49b4-b9ef-0680b7f1031d");

    public static final ExtKey POSITIONAL = new ExtKey("AAA_JDBC_CLI_POSITIONAL", String.class, "0b3389bf-93b1-4a5a-bb56-361afa32dff3");

    public static final ExtKey THROWABLES = new ExtKey("AAA_JDBC_CLI_THROWABLE", Collection/**<Throwable.class>*/.class, "a2cc22b4-27f5-4551-b507-9e409697c340");
    /** suffix of cli parameters to parse */
    public static final ExtKey TAIL = new ExtKey("AAA_JDBC_CLI_TAIL", List/*<String>*/.class, "49deb728-8aae-4c93-a491-91642fa304c2");

    public static final ExtKey SEARCH_FILTER = new ExtKey("AAA_JDBC_CLI_SEARCH_FILTER", String.class, "2bbd200f-4362-436b-840f-dafe7c968e1e");
    public static final ExtKey SEARCH_RESULT = new ExtKey("AAA_JDBC_CLI_SEARCH_RESULT", Collection/*<ExtMap>*/.class, "b750f37e-fae5-4126-9516-8a62d528eb05");

    public static final ExtKey SHOW_TEMPLATE = new ExtKey("AAA_JDBC_CLI_SHOW_TEMPLATE", String.class, "32f09d46-aa79-474b-99b6-a8e90716d1b5");
}
