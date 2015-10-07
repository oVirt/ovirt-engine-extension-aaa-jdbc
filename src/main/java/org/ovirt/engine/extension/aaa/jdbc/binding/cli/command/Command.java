package org.ovirt.engine.extension.aaa.jdbc.binding.cli.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.Cli;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.ContextKeys;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.parser.ArgumentsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commands are nested. example: "PROGRAM_NAME user add mike" will invoke:
 * root, root.user and root.user.add.
 */
public abstract class Command {
    private static final Logger LOG = LoggerFactory.getLogger(Command.class);

    public abstract String getName();
    public List<String> getSubModules() {
        return Collections.emptyList();
    }
    /** is a positional argument expected before the module options. */
    public boolean entityNameExpected() {
        return false;
    }
    /**
     * This method is responsible for:
     * - parsing preceding positional argument expected before the keyword arguments according to getPosCount().
     * - parsing keyword arguments according to getName()
     * if getSubModules().size() > 1:
     * - parsing name of next expected command and invoking it
     */
    public abstract void invoke(ExtMap context, Map<String, Object> args);

    public void invoke(ExtMap context) {
        if (entityNameExpected()) { // e.g user add 'mike'
            putNextPositional(context);
            LOG.trace("entity name is: {}", context.get(ContextKeys.POSITIONAL));
        }
        Map<String, Object> args = null;

        if (!getName().startsWith("_")) { // get keyword args
            args = nextArgs(context, context.containsKey(ContextKeys.EXIT_STATUS));
        }

        if (!context.containsKey(ContextKeys.EXIT_STATUS)) { // invoke command
            LOG.trace("Invoking: {} with args: {}", getName(), args);

            invoke(context, args);
        }

        if (!context.containsKey(ContextKeys.EXIT_STATUS) && getSubModules().size() > 0) { // get next
            putNextPositional(context);
            String name = context.get(ContextKeys.POSITIONAL, String.class);
            LOG.trace("next: {}", name);

            if (
                !this.getSubModules().contains(name)
            ) {
                addContextMessage(context, true,
                     Formatter.format(
                         "{}. {}\n",
                         (
                             name != null ?
                             "unexpected command: " + name :
                             "no command provided"
                         ),
                         "options: " + StringUtils.join(getSubModules(), ", ") + ", help")
                );
                context.putIfAbsent(ContextKeys.EXIT_STATUS, Cli.ARGUMENT_PARSING_ERROR);
            }
            if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                context.remove(ContextKeys.POSITIONAL);
                Cli.getCommands().get(this.getName() + "-" + name).invoke(context);
            }
        }
    }

    private void putNextPositional(ExtMap context) {
        String name;
        @SuppressWarnings("unchecked")
        List<String> tail = context.get(ContextKeys.TAIL, List.class);
        if (tail.size() > 0 && !tail.get(0).startsWith("--")) {
            name = tail.remove(0);
            context.put(ContextKeys.POSITIONAL, name);
            context.put(ContextKeys.TAIL, tail);
        } else {
            context.put(ContextKeys.EXIT_STATUS, Cli.ARGUMENT_PARSING_ERROR);
        }
    }

    private Map<String, Object> nextArgs(ExtMap context, boolean showHelp) {
        Map<String, String> contextSubstitutions = new HashMap<>();
        contextSubstitutions.put("@ENGINE_ETC@", System.getProperty("org.ovirt.engine.aaa.jdbc.engineEtc"));
        contextSubstitutions.put("@PROGRAM_NAME@", System.getProperty("org.ovirt.engine.aaa.jdbc.programName"));
        contextSubstitutions.put("@MODULE_LIST@", StringUtils.join(this.getSubModules(), "\n  ") + "\n  help");

        Map<String, Object> parsed;
        ArgumentsParser argumentsParser = new ArgumentsParser(
            Cli.class.getResourceAsStream("arguments.properties"),
            this.getName()
        );
        argumentsParser.getSubstitutions().putAll(contextSubstitutions);
        @SuppressWarnings("unchecked")
        List<String> tail = context.get(ContextKeys.TAIL, List.class);
        argumentsParser.parse(tail); // updates tail.
        parsed = argumentsParser.getParsedArgs();

        if (showHelp || !context.containsKey(ContextKeys.EXIT_STATUS)) {

            if (showHelp || (Boolean)parsed.get("help") ||  (tail.size() > 0 && tail.get(0).equals("help"))) {
                addContextMessage(context, false, argumentsParser.getUsage());
                context.putIfAbsent(ContextKeys.EXIT_STATUS, Cli.SUCCESS);
            } else {
                List<Throwable> errors = argumentsParser.getErrors();
                if (errors.size() > 0) {
                    for (Throwable thr: errors) {
                        context.get(ContextKeys.THROWABLES, List.class).add(thr);
                        context.get(ContextKeys.ERR_MESSAGES, List.class).add(thr.getMessage());
                    }
                    context.mput(ContextKeys.EXIT_STATUS, Cli.ARGUMENT_PARSING_ERROR);
                }
            }
        }
        return parsed;
    }

    protected static void addContextMessage(ExtMap context, boolean error, String message) {
        ExtKey key;
        if (error) {
            key = ContextKeys.ERR_MESSAGES;
        } else {
            key = ContextKeys.OUT_MESSAGES;
        }
        ((List<String>) context.get(key, List.class)).add(message);
    }
}
