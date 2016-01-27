package org.ovirt.engine.extension.aaa.jdbc.binding.cli;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.ExtUUID;
import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.binding.Config;
import org.ovirt.engine.extension.aaa.jdbc.binding.api.ExtensionUtils;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.command.Command;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.command.GroupManageShowCommand;
import org.ovirt.engine.extension.aaa.jdbc.core.Authentication;
import org.ovirt.engine.extension.aaa.jdbc.core.Authorization;
import org.ovirt.engine.extension.aaa.jdbc.core.EnvelopePBE;
import org.ovirt.engine.extension.aaa.jdbc.core.Schema;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.DataSourceProvider;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cli {

    private static final List<String> SENSISTIVE_DATA = new ArrayList<>();
    static {
        SENSISTIVE_DATA.add("config.datasource.dbpassword");
    }

    /** Exit Statuses */
    public static final int SUCCESS = 0;
    public static final int GENERAL_ERROR = 1;
    public static final int ARGUMENT_PARSING_ERROR = 2;
    public static final int SQL_ERROR = 3;
    public static final int NOT_FOUND = 4;
    public static final int ALREADY_EXISTS = 5;


    /** Search */
    private static final Map<String, ExtKey> cliNameToApiKey = new HashMap<>();

    /**
     * To print output:
     */
    private static final Map<ExtUUID, String> ENTITY_NAMES = new HashMap<>();

    //Commands which are defined in their own class (meaning not statically here
    //in Cli.java), may need to access this map.
    public static Map<String, Command> getCommands() {
        return commands;
    }

    private static final Logger LOG = LoggerFactory.getLogger(Cli.class);

    private static final Map<String, Command> commands = new HashMap<>();

    public static final ExtMap INSERT_DEFAULTS = new ExtMap().mput(
        Schema.UserKeys.UNLOCK_TIME,
        0L
    ).mput(
        Schema.UserKeys.SUCCESSFUL_LOGIN,
        0L
    ).mput(
        Schema.UserKeys.UNSUCCESSFUL_LOGIN,
        0L
    ).mput(
        Schema.UserKeys.PASSWORD_VALID_TO,
        0L
    ).mput(
        Schema.UserKeys.NOPASS,
        false
    ).mput(
        Schema.UserKeys.DISABLED,
        false
    );

    static {
        ENTITY_NAMES.put(Schema.Entities.USER, "user");
        ENTITY_NAMES.put(Schema.Entities.GROUP, "group");
        ENTITY_NAMES.put(Schema.Entities.SETTINGS, "setting");

        cliNameToApiKey.put("g.name", Authz.GroupRecord.NAME);
        cliNameToApiKey.put("g.id", Authz.GroupRecord.ID);
        cliNameToApiKey.put("g.displayName", Authz.GroupRecord.DISPLAY_NAME);
        cliNameToApiKey.put("g.description", Schema.AuthzInternal.GROUP_DESCRIPTION);
        cliNameToApiKey.put("g.namespace", Authz.GroupRecord.NAMESPACE);

        cliNameToApiKey.put("u.name", Authz.PrincipalRecord.NAME);
        cliNameToApiKey.put("u.id", Authz.PrincipalRecord.ID);
        cliNameToApiKey.put("u.displayName", Authz.PrincipalRecord.DISPLAY_NAME);
        cliNameToApiKey.put("u.description", Schema.AuthzInternal.USER_DESCRIPTION);
        cliNameToApiKey.put("u.namespace", Authz.PrincipalRecord.NAMESPACE);
        cliNameToApiKey.put("u.email", Authz.PrincipalRecord.EMAIL);
        cliNameToApiKey.put("u.firstName", Authz.PrincipalRecord.FIRST_NAME);
        cliNameToApiKey.put("u.lastName", Authz.PrincipalRecord.LAST_NAME);
        cliNameToApiKey.put("u.department", Authz.PrincipalRecord.DEPARTMENT);
        cliNameToApiKey.put("u.title", Authz.PrincipalRecord.TITLE);

        for (Command cmd: Arrays.asList(
            new Command() {
                @Override
                public String getName() {
                    return "root";
                }

                @Override
                public List<String> getSubModules() {
                    return Arrays.asList("user", "group", "group-manage", "query", "settings");
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {

                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        setupLogging((Level) args.get("log-level"));
                        context.put(ContextKeys.LOGGING_STARTED, true);
                        LOG.trace("Logging started.");

                        context.put(
                            Schema.InvokeKeys.DATA_SOURCE,
                            new DataSourceProvider(
                                loadPropertiesFromFile((String) args.get("db-config"))
                            ).provide()
                        );

                        // get settings
                        context.put(Schema.InvokeKeys.ENTITY, Schema.Entities.SETTINGS);
                        commands.get("_schema-get").invoke(context);
                        context.remove(Schema.InvokeKeys.ENTITY);

                        try {
                            ExtensionUtils.checkDbVersion(
                                context.<DataSource>get(Schema.InvokeKeys.DATA_SOURCE),
                                (String) args.get("db-config")
                            );
                        } catch (SQLException | IOException e) {
                            context.put(ContextKeys.EXIT_STATUS, GENERAL_ERROR);
                            addContextMessage(context, true, e.getMessage());
                            context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                        }
                    }
                    if (!context.containsKey(ContextKeys.EXIT_STATUS) && (Boolean) args.get("version")) {
                        System.out.print(
                            Formatter.format(
                                "package name: {}\nversion: {}\n",
                                Config.PACKAGE_NAME,
                                Config.PACKAGE_VERSION
                            )
                        );
                        context.put(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user";
                }

                @Override
                public List<String> getSubModules() {
                    return Arrays.asList("add", "edit", "delete", "unlock", "password-reset", "show");
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.USER);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-add";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.put(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.INSERT);
                    putUserAddKeys(context, args);
                    String name = context.get(ContextKeys.POSITIONAL);
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_schema-modify").invoke(context);
                    }

                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        addContextMessage(context, false, Formatter.format(
                            "Note: by default created user cannot log in. see:\n{} user password-reset --help.",
                            System.getProperty("org.ovirt.engine.aaa.jdbc.programName")
                        ));
                        context.put(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-edit";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {

                    context.mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                    putUserEditKeys(context, args);
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_schema-modify").invoke(context);
                        context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-delete";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        context.put(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.DELETE);
                        context.put(Schema.InvokeKeys.ENTITY_KEYS, new ExtMap().mput(Schema.UserIdentifiers.USERNAME,
                                                                                     context.get(ContextKeys.POSITIONAL)
                        ));

                    }
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_schema-modify")
                            .invoke(context);
                        context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-unlock";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        context.put(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                        context.put(
                            Schema.InvokeKeys.ENTITY_KEYS,
                            new ExtMap().mput(Schema.UserIdentifiers.USERNAME, context.get(ContextKeys.POSITIONAL))
                                .mput(Schema.UserKeys.UNLOCK_TIME, System.currentTimeMillis())
                        );
                        commands.get("_schema-modify").invoke(context);
                        context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-password-reset";
                }

                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    try {
                        String newPass = null;
                        boolean nopass = false;
                        boolean forcePassword = false;
                        context.put(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                        putUserResetPassParams(context, args);
                        if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                            newPass =
                                context.get(Schema.InvokeKeys.ENTITY_KEYS, ExtMap.class)
                                .get(Schema.UserKeys.PASSWORD, String.class);
                            nopass =
                                context.get(Schema.InvokeKeys.ENTITY_KEYS, ExtMap.class)
                                .get(Schema.UserKeys.NOPASS, Boolean.class, false);
                            forcePassword = context.get(Schema.InvokeKeys.ENTITY_KEYS, ExtMap.class)
                                .get(Schema.UserKeys.FORCE_PASSWORD);
                        }
                        Schema.User user = null;
                        if (!context.containsKey(ContextKeys.EXIT_STATUS)) { // need to fetch user for pass history
                            commands.get("_schema-get").invoke(context);
                            user = context.get(Schema.InvokeKeys.USER_RESULT, Schema.User.class);
                            if (user == null) {
                                context.put(ContextKeys.EXIT_STATUS, NOT_FOUND);
                                addContextMessage(context, true, Formatter.format("user {} not found",
                                    context.get(ContextKeys.POSITIONAL)
                                ));
                            }
                        }
                        if (!context.containsKey(ContextKeys.EXIT_STATUS) &&
                            !nopass &&
                            !forcePassword
                        ) {
                            // test pass history & complexity
                            Authentication authentication =
                                new Authentication(
                                    context.get(
                                        Schema.InvokeKeys.DATA_SOURCE,
                                        DataSource.class
                                    )
                                );
                            authentication.update(null, context.get(Schema.InvokeKeys.SETTINGS_RESULT, ExtMap.class));
                            Authentication.AuthResponse authResponse =
                                authentication.checkCredChange(
                                    user,
                                    newPass
                                );
                            if (authResponse.result != Authn.AuthResult.SUCCESS) {
                                context.mput(ContextKeys.EXIT_STATUS, GENERAL_ERROR);
                                addContextMessage(context, true, authResponse.baseMsg);
                            }
                        }
                        if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                            ExtMap settings = context.get(Schema.InvokeKeys.SETTINGS_RESULT, ExtMap.class);
                            context.get(Schema.InvokeKeys.ENTITY_KEYS, ExtMap.class)
                            .mput(
                                Schema.UserKeys.PASSWORD,
                                newPass == null ?
                                null :
                                EnvelopePBE.encode(
                                    settings.get(Schema.Settings.PBE_ALGORITHM, String.class),
                                    settings.get(Schema.Settings.PBE_KEY_SIZE, Integer.class),
                                    settings.get(Schema.Settings.PBE_ITERATIONS, Integer.class),
                                    null,
                                    newPass
                                )
                            ).mput(
                                Schema.UserKeys.OLD_PASSWORD,
                                user.getPassword()
                            );
                            commands.get("_schema-modify").invoke(context);
                            context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                        }
                    } catch (IOException | GeneralSecurityException e) {
                        context.put(ContextKeys.EXIT_STATUS, GENERAL_ERROR);
                        addContextMessage(context, true, e.getMessage());
                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-user-show";
                }

                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    if (args.containsKey("attribute")) {
                        context.put(
                            ContextKeys.SHOW_TEMPLATE,
                            String.format(
                                "user-%s",
                                args.get("attribute")
                            )
                        );
                    }
                    context.put(
                        ContextKeys.SEARCH_FILTER,
                        Formatter.format("{} = {}",
                            Schema.SEARCH_KEYS.get(Authz.PrincipalRecord.NAME),
                            Formatter.escapeString(context.get(ContextKeys.POSITIONAL, String.class))
                        )
                    );
                    context.mput(Global.InvokeKeys.SEARCH_CONTEXT,
                     new ExtMap().mput(Global.SearchContext.PAGE_SIZE, 1)
                     .mput(Global.SearchContext.IS_PRINCIPAL, true)
                     .mput(Global.SearchContext.RECURSIVE, false)
                     .mput(Global.SearchContext.WITH_GROUPS, true)
                     .mput(Global.SearchContext.ALL_ATTRIBUTES, true)
                    );

                    commands.get("_search").invoke(context);
                    if (
                        context.get(ContextKeys.SEARCH_RESULT) == null ||
                        context.get(ContextKeys.SEARCH_RESULT, Collection.class).size() == 0
                    ) {
                        addContextMessage(context, true, Formatter.format(
                            "user {} not found",
                            context.get(ContextKeys.POSITIONAL)
                        ));
                        context.put(ContextKeys.EXIT_STATUS, NOT_FOUND);
                    }


                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_show")
                            .invoke(context);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group";
                }

                @Override
                public List<String> getSubModules() {
                    return Arrays.asList("add", "edit", "delete", "show");
                }


                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.GROUP);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-add";
                }

                public boolean entityNameExpected() {
                    return true; // group name expected
                }


                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.INSERT);
                    context.put(Schema.InvokeKeys.ENTITY_KEYS,
                                getGroupKeys(args, context.get(ContextKeys.POSITIONAL, String.class))
                    );

                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_schema-modify")
                            .invoke(context);
                        context.put(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-edit";
                }

                public boolean entityNameExpected() {
                    return true; // group name expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                    context.put(Schema.InvokeKeys.ENTITY_KEYS, getGroupKeys(args, context.get(ContextKeys.POSITIONAL, String.class)));

                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_schema-modify")
                            .invoke(context);
                        context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-delete";
                }

                public boolean entityNameExpected() {
                    return true; // group name expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.put(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.DELETE);
                    context.put(Schema.InvokeKeys.ENTITY_KEYS,
                                new ExtMap().mput(
                                    Schema.GroupIdentifiers.NAME,
                                    context.get(ContextKeys.POSITIONAL)
                                )
                    );
                    commands.get("_schema-modify").invoke(context);
                    context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-show";
                }

                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    getGroup(context);
                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_show").invoke(context);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-manage";
                }

                @Override
                public List<String> getSubModules() {
                    return Arrays.asList("useradd", "userdel", "groupadd", "groupdel", "show");
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-manage-useradd";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.USER)
                    .mput(
                        Schema.InvokeKeys.ENTITY_KEYS,
                        new ExtMap().mput(Schema.UserIdentifiers.USERNAME, args.get("user"))
                            .mput(Schema.SharedKeys.ADD_GROUP, context.get(ContextKeys.POSITIONAL))
                    );
                    commands.get("_schema-modify").invoke(context);
                    context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-manage-userdel";
                }

                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.USER)
                        .mput(
                            Schema.InvokeKeys.ENTITY_KEYS,
                            new ExtMap().mput(Schema.UserIdentifiers.USERNAME, args.get("user"))
                                .mput(Schema.SharedKeys.REMOVE_GROUP, context.get(ContextKeys.POSITIONAL))
                        );
                    commands.get("_schema-modify").invoke(context);
                    context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-manage-groupadd";
                }

                @Override
                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.GROUP)
                        .mput(
                            Schema.InvokeKeys.ENTITY_KEYS,
                            new ExtMap().mput(Schema.GroupIdentifiers.NAME, args.get("group"))
                                .mput(Schema.SharedKeys.ADD_GROUP, context.get(ContextKeys.POSITIONAL))
                        );

                    commands.get("_schema-modify")
                        .invoke(context);
                    context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-group-manage-groupdel";
                }

                public boolean entityNameExpected() {
                    return true; // username expected
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.GROUP)
                        .mput(
                            Schema.InvokeKeys.ENTITY_KEYS,
                            new ExtMap().mput(Schema.GroupIdentifiers.NAME, args.get("group"))
                                .mput(Schema.SharedKeys.REMOVE_GROUP, context.get(ContextKeys.POSITIONAL))
                        );
                    commands.get("_schema-modify")
                        .invoke(context);
                    context.putIfAbsent(ContextKeys.EXIT_STATUS, SUCCESS);

                }
            },
            new GroupManageShowCommand(),
            new Command() {
                @Override
                public String getName() {
                    return "root-settings";
                }

                @Override
                public List<String> getSubModules() {
                    return Arrays.asList("show", "set");
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.ENTITY, Schema.Entities.SETTINGS);
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-settings-show";
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    ExtMap settings = context.get(Schema.InvokeKeys.SETTINGS_RESULT, ExtMap.class);
                    ExtMap descriptions = (ExtMap) settings.remove(Schema.Settings.SETTING_DESCRIPTIONS);
                    for (Map.Entry<ExtKey, Object> entry : settings.entrySet()) {
                        if (
                            ((String) args.get("name")).toUpperCase().equals("ALL") ||
                            ((String) args.get("name")).toUpperCase().equals(entry.getKey().getUuid().getName())
                        ) {
                            System.out.print(
                                Formatter.format("-- setting --\nname: {}\nvalue: {}\ntype: {}\ndescription: {}\n",
                                    entry.getKey().getUuid().getName(),
                                    entry.getValue(),
                                    entry.getKey().getType(),
                                    descriptions.get(entry.getKey())
                                )
                            );
                        }
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-settings-set";
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    context.mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE);
                    ExtMap settings = context.get(Schema.InvokeKeys.SETTINGS_RESULT, ExtMap.class);
                    for (Map.Entry<ExtKey, Object> entry : settings.entrySet()) {
                        ExtKey key = entry.getKey();
                        if (((String) args.get("name")).toUpperCase()
                            .equals(key.getUuid().getName())) {
                            try {
                                context.mput(Schema.InvokeKeys.ENTITY_KEYS,
                                     new ExtMap().mput(
                                         key,
                                         key.getType().getConstructor(String.class)
                                         .newInstance((String) args.get("value"))
                                     )
                                );
                            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                                throw new RuntimeException(
                                    Formatter.format(
                                        "Could not convert setting to expected type. value: {} type: {}",
                                        args.get("value"),
                                        key.getType()
                                    ),
                                    e
                                );
                            }
                            commands.get("_schema-modify").invoke(context);
                        }
                    }

                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "root-query";
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    String what = (String) args.get("what");
                    boolean isPrincipal = what.toUpperCase().equals("USER");

                    @SuppressWarnings("unchecked")
                    List<String> filterPatterns = (List<String>) args.get("pattern");
                    filterPatterns = (filterPatterns != null ? filterPatterns : Collections.<String>emptyList());
                    StringBuilder filter = new StringBuilder("true AND ");
                    for (String filterPattern : filterPatterns) {
                        String val = filterPattern.split("=", 2)[1];
                        if (val.endsWith("*")) {
                            val = val.substring(0, val.length() - 1).concat("%");
                        }
                        filter.append(
                            Schema.SEARCH_KEYS.get(
                                cliNameToApiKey.get(
                                    (isPrincipal ? "u." : "g.") + filterPattern.split("=", 2)[0]
                                )
                            )
                        ).append(" ").append(Schema.OPERATORS.get(Schema.AuthzInternal.LIKE)).append(" ")
                        .append(Formatter.escapeString(val)).append(" AND ");
                    }
                    filter.setLength(filter.length() - 5);
                    context.mput(
                        ContextKeys.SEARCH_FILTER,
                        filter.toString()
                    ).mput(
                        Global.InvokeKeys.SEARCH_CONTEXT,
                        new ExtMap().mput(Global.SearchContext.PAGE_SIZE, 100)
                        .mput(Global.SearchContext.IS_PRINCIPAL, isPrincipal)
                        .mput(Global.SearchContext.RECURSIVE, false)
                        .mput(Global.SearchContext.WITH_GROUPS, true)
                        .mput(Global.SearchContext.ALL_ATTRIBUTES, true)
                    );

                    commands.get("_search").invoke(context);

                    if (!context.containsKey(ContextKeys.EXIT_STATUS)) {
                        commands.get("_show").invoke(context);
                    }
                }
            },
            /**
             * Following are internal methods that cannot be invoked from command line.
             */
            new Command() {
                @Override
                public String getName() {
                    return "_schema-get";
                }

                @SuppressWarnings("unchecked")
                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    try {
                        context.putAll(
                            Schema.get(
                                new ExtMap().mput(Schema.InvokeKeys.DATA_SOURCE, context.get(Schema.InvokeKeys.DATA_SOURCE))
                                .mput(Schema.InvokeKeys.ENTITY, context.get(Schema.InvokeKeys.ENTITY))
                                .mput(Schema.InvokeKeys.ENTITY_KEYS, context.get(Schema.InvokeKeys.ENTITY_KEYS))
                                .mput(Schema.InvokeKeys.SETTINGS_RESULT, context.get(Schema.InvokeKeys.SETTINGS_RESULT))
                                .mput(Schema.InvokeKeys.USER_RESULT, context.get(Schema.InvokeKeys.USER_RESULT))
                            )
                        );
                    } catch (SQLException e) {
                        context.put(ContextKeys.EXIT_STATUS, SQL_ERROR);
                        addContextMessage(context, true, e.getMessage());

                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "_schema-modify";
                }

                @SuppressWarnings("unchecked")
                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    try {
                        Integer modType = context.get(Schema.InvokeKeys.MODIFICATION_TYPE, Integer.class);
                        String modName = "add";
                        if (modType == Sql.ModificationTypes.DELETE) {
                            modName = "delet";
                        } else if (modType == Sql.ModificationTypes.UPDATE) {
                            modName = "updat";
                        }
                        System.out.print(
                            Formatter.format(
                                "{}ing {} {}...\n",
                                modName,
                                ENTITY_NAMES.get(context.get(Schema.InvokeKeys.ENTITY, ExtUUID.class)),
                                context.get(ContextKeys.POSITIONAL) != null ? // settings have no name
                                context.get(ContextKeys.POSITIONAL) :
                                ""
                            ));
                        ExtMap modification =
                            new ExtMap().mput(Schema.InvokeKeys.ENTITY, context.get(Schema.InvokeKeys.ENTITY))
                            .mput(Schema.InvokeKeys.ENTITY_KEYS, context.get(Schema.InvokeKeys.ENTITY_KEYS))
                            .mput(Schema.InvokeKeys.USER_RESULT, context.get(Schema.InvokeKeys.USER_RESULT))
                            .mput(Schema.InvokeKeys.MODIFICATION_TYPE, modType) // removing
                            .mput(Schema.InvokeKeys.DATA_SOURCE, context.get(Schema.InvokeKeys.DATA_SOURCE));
                        LOG.trace(Formatter.format("executing modification: {}", modification));
                        Schema.modify(modification);
                        System.out.print(
                            Formatter.format(
                                "{} {}ed successfully\n",
                                ENTITY_NAMES.get(context.get(Schema.InvokeKeys.ENTITY, ExtUUID.class)),
                                modName
                            )
                        );
                    } catch (Schema.EntityNotFoundException e) {
                        addContextMessage(context, true, e.getMessage());
                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                        context.put(ContextKeys.EXIT_STATUS, NOT_FOUND);
                    } catch (Schema.EntityAlreadyExists e) {
                        addContextMessage(context, true, e.getMessage());
                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                        context.put(ContextKeys.EXIT_STATUS, ALREADY_EXISTS);
                    } catch (SQLException e) {
                        addContextMessage(context, true, e.getMessage());
                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                        context.put(ContextKeys.EXIT_STATUS, SQL_ERROR);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "_search";
                }


                @SuppressWarnings("unchecked")
                @Override
                public  void invoke(ExtMap context, Map<String, Object> args) {
                    Collection<ExtMap> res = null;
                    try {
                        Authorization authorization = new Authorization(context.get(Schema.InvokeKeys.DATA_SOURCE, DataSource.class));
                        authorization.update(null, context.get(Schema.InvokeKeys.SETTINGS_RESULT, ExtMap.class));
                        context.mput(ContextKeys.SEARCH_RESULT,
                             authorization.getResults(
                                 context.get(ContextKeys.SEARCH_FILTER, String.class),
                                 context.get(Global.InvokeKeys.SEARCH_CONTEXT, ExtMap.class)
                             )
                        );

                    } catch (SQLException e) {
                        context.put(ContextKeys.EXIT_STATUS, SQL_ERROR);
                        addContextMessage(context, true, e.getMessage());
                        context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
                    }
                }
            },
            new Command() {
                @Override
                public String getName() {
                    return "_show";
                }

                private String formatValue(Map.Entry<ExtKey, Object> entry) {
                    if (entry.getValue() == null) {
                        return "";
                    } else if (Schema.UserKeys.PASSWORD_VALID_TO.equals(entry.getKey()) ||
                            Schema.UserKeys.SUCCESSFUL_LOGIN.equals(entry.getKey()) ||
                            Schema.UserKeys.UNLOCK_TIME.equals(entry.getKey()) ||
                            Schema.UserKeys.UNSUCCESSFUL_LOGIN.equals(entry.getKey()) ||
                            Schema.UserKeys.VALID_FROM.equals(entry.getKey()) ||
                            Schema.UserKeys.VALID_TO.equals(entry.getKey())) {
                        return DateUtils.toISO((Long) entry.getValue());
                    } else {
                        return entry.getValue().toString();
                    }
                }

                @Override
                public void invoke(ExtMap context, Map<String, Object> args) {
                    Properties templates = loadPropertiesFromJar("entity-templates.properties");
                    String providedTemplate = null;
                    if (context.containsKey(ContextKeys.SHOW_TEMPLATE)) {
                        providedTemplate = templates.get(context.get(ContextKeys.SHOW_TEMPLATE)).toString();
                    }

                    @SuppressWarnings("unchecked")
                    Collection<ExtMap> results = context.get(
                        ContextKeys.SEARCH_RESULT,
                        Collection.class,
                        Collections.emptyList()
                    );

                    for (ExtMap result : results) {
                        String out =
                            providedTemplate == null ?
                                (
                                    result.containsKey(Authz.PrincipalRecord.ID) ?
                                    templates.get("user").toString() :
                                    templates.get("group").toString()
                                ) :
                                providedTemplate;
                        for (Map.Entry<ExtKey, Object> entry : result.entrySet()) {
                            Matcher m = Pattern.compile(
                                String.format(
                                    "@%s@",
                                    entry.getKey().getUuid().getUuid()
                                )
                            ).matcher(out);
                            out = m.replaceAll(
                                    Matcher.quoteReplacement(formatValue(entry))
                            );
                        }
                        addContextMessage(context, false, out);
                    }
                    context.put(ContextKeys.EXIT_STATUS, SUCCESS);
                }
            }
        )) {
            commands.put(cmd.getName(), cmd);
        }
    }

    public static void main(String[] args) {
        ExtMap context = new ExtMap();

        try {
            // init context
            context.mput(ContextKeys.TAIL, new LinkedList<>(Arrays.asList(args)));
            context.put(Schema.InvokeKeys.SETTINGS_RESULT, new ExtMap());
            context.put(ContextKeys.THROWABLES, new ArrayList<>());
            context.put(ContextKeys.ERR_MESSAGES, new ArrayList<>());
            context.put(ContextKeys.OUT_MESSAGES, new ArrayList<>());

            // Invoke commands
            commands.get("root").invoke(context);


            for (String msg: context.<List<String>>get(ContextKeys.ERR_MESSAGES)) {
                System.err.print(newLine(msg));
            }


            for (String msg: context.<List<String>>get(ContextKeys.OUT_MESSAGES)) {
                System.out.print(newLine(msg));
            }

            for (Throwable thr: context.<List<Throwable>>get(ContextKeys.THROWABLES)) {
                LOG.debug("Exception", thr);
            }
            System.exit(
                context.get(ContextKeys.EXIT_STATUS, Integer.class, SUCCESS)
            );
        } catch (Throwable t) {
            if (context.get(ContextKeys.LOGGING_STARTED, Boolean.class, false)) {
                LOG.error("Unexpected Exception invoking Cli: {}", t.getMessage());
                LOG.debug("exception", t);
            } else {
                t.printStackTrace();
            }
            System.exit(GENERAL_ERROR);
        }
    }

    private static void addContextMessage(ExtMap context, boolean error, String message) {
        ExtKey key;
        if (error) {
            key = ContextKeys.ERR_MESSAGES;
        } else {
            key = ContextKeys.OUT_MESSAGES;
        }
        context.<List<String>>get(key).add(message);
    }

    private static Properties loadPropertiesFromFile(String filename) {
        try (FileReader reader = new FileReader(new File(filename))) {
            Properties p = new Properties();
            p.load(reader);
            LOG.trace("read properties from {}:", filename);
            for(Map.Entry<Object, Object> e: p.entrySet()) {
                LOG.trace(
                    "{}=>{}",
                    e.getKey(),
                    SENSISTIVE_DATA.contains(e.getKey()) ? "****" : e.getValue()
                );
            }
            return p;
        } catch (Exception e) {
            throw new RuntimeException("Could not read properties from: " + filename, e);
        }
    }

    private static Properties loadPropertiesFromJar(String filename) {
        try (
            InputStream is = Cli.class.getResourceAsStream(filename);
            Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"))
        ) {
            Properties p = new Properties();
            p.load(reader);
            LOG.trace("read properties from {}:", filename);
            for(Map.Entry<Object, Object> e: p.entrySet()) {
                LOG.trace("{}=>{}", e.getKey(), e.getValue());
            }
            return p;
        } catch (Exception e) {
            throw new RuntimeException("Could not read properties from: " + filename, e);
        }
    }
    private static void setupLogging(Level level) {
        java.util.logging.Logger jdbc = java.util.logging.Logger.getLogger("org.ovirt.engine.extension.aaa.jdbc");
        jdbc.setLevel(level);
        Handler[] handlers = jdbc.getHandlers();
        for (Handler handler : handlers) {
            jdbc.removeHandler(handler);
        }
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        jdbc.addHandler(handler);
    }

    /**
     * Put Schema.InvokeKeys.ENTITY_KEYS OR Cli.ContextKeys.EXIT_STATUS into context
     *
     * @param context current context
     * @param args arguments provided by user and parsed by argumentsParser
     * @return context after addition
     */
    private static ExtMap putUserAddKeys(ExtMap context, Map<String, Object> args) {
        ExtMap keys = new ExtMap();

        try {
            keys.putAll(INSERT_DEFAULTS);
            keys.mput(
                Schema.UserIdentifiers.USERNAME,
                context.get(ContextKeys.POSITIONAL)
            ).mput(
                Schema.UserKeys.NEW_USERNAME,
                context.get(ContextKeys.POSITIONAL)
            ).mput(
                Schema.UserKeys.PASSWORD,
                ""
            ).mput(
                Schema.UserKeys.VALID_FROM,
                (
                    args.get("account-valid-from") != null ?
                    DateUtils.fromISO((String) args.get("account-valid-from")) :
                    System.currentTimeMillis()
                )
            ).mput(
                Schema.UserKeys.VALID_TO,
                (
                    args.get("account-valid-to") != null?
                    DateUtils.fromISO((String)args.get("account-valid-to")) :
                    DateUtils.add(System.currentTimeMillis(), Calendar.YEAR, 200)
                )
            ).mput(
                Schema.UserKeys.LOGIN_ALLOWED,
                args.get("account-login-time") != null ? args.get("account-login-time") : StringUtils.leftPad("", 336, '1')
            ).mput(
                Schema.UserKeys.UUID,
                args.get("id")
            ).mput(
                Schema.UserKeys.DISABLED,
                getDefFlag(args, "disabled", false)
            ).mput(
                Schema.UserKeys.NOPASS,
                getDefFlag(args, "nopass", false)
            ).mput(
                Schema.SharedKeys.ATTRIBUTES,
                createAttributes(args)
            );
            context.put(Schema.InvokeKeys.ENTITY_KEYS, keys);
        } catch (ParseException e) {
            addContextMessage(context, true, e.getMessage());
            context.mput(ContextKeys.EXIT_STATUS, ARGUMENT_PARSING_ERROR);

            context.<List<Throwable>>get(ContextKeys.THROWABLES).add(e);
        }
        return context;
    }

    /**
     * Put Schema.InvokeKeys.ENTITY_KEYS OR Cli.ContextKeys.EXIT_STATUS into context
     *
     * @param context the Execution context to add on
     * @param args arguments provided by user and parsed by argumentsParser
     * @return context after addition
     */
    private static ExtMap putUserEditKeys(ExtMap context, Map<String, Object> args) {
        ExtMap keys = new ExtMap();

        try {
            keys.mput(
                Schema.UserIdentifiers.USERNAME,
                context.get(ContextKeys.POSITIONAL)
            ).mput(
                Schema.UserKeys.NEW_USERNAME,
                args.get("new-name")
            ).mput(
                Schema.UserKeys.PASSWORD_VALID_TO,
                (
                    args.get("password-valid-to") != null ?
                    DateUtils.fromISO((String)args.get("password-valid-to")):
                    null
                )
            ).mput(
                Schema.UserKeys.VALID_FROM,
                (
                    args.get("account-valid-from") != null ?
                    DateUtils.fromISO((String)args.get("account-valid-from")):
                    null
                )
            ).mput(
                Schema.UserKeys.VALID_TO,
                (
                    args.get("account-valid-to") != null ?
                    DateUtils.fromISO((String)args.get("account-valid-to")):
                    null
                )
            ).mput(
                Schema.UserKeys.LOGIN_ALLOWED,
                args.get("account-login-time")
            ).mput(
                Schema.UserKeys.UUID,
                args.get("id")
            ).mput(
                Schema.UserKeys.DISABLED,
                getDefFlag(args, "disabled", null)
            ).mput(
                Schema.UserKeys.NOPASS,
                getDefFlag(args, "nopass", null)
            ).mput(
                Schema.SharedKeys.ATTRIBUTES,
                createAttributes(args)
            );
        } catch (ParseException e) {
            addContextMessage(context, true, e.getMessage());
            context.mput(ContextKeys.EXIT_STATUS, ARGUMENT_PARSING_ERROR);
        }
        return context.mput(Schema.InvokeKeys.ENTITY_KEYS, keys);
    }

    /**
     * Create user attributes collection
     *
     * @param args user input map
     * @return a collection of ExtMaps each representing an attribute.
     */
    private static Collection<ExtMap> createAttributes(Map<String, Object> args) {
        Collection<ExtMap> attributes = new ArrayList<>();
        if (args.get("attribute") != null) {

            @SuppressWarnings("unchecked")
            List<String> attributeDescriptors = (List<String>) args.get("attribute");
            for (String attributeDescriptor: attributeDescriptors) {
                attributes.add(
                    new ExtMap().mput(
                        Schema.SharedKeys.ATTRIBUTE_NAME,
                        attributeDescriptor.split("=", 2)[0]
                    ).mput(
                        Schema.SharedKeys.ATTRIBUTE_VALUE,
                        attributeDescriptor.split("=", 2)[1]
                    )
                );
            }
        }
        return attributes;
    }

    private static String readPasswordInteractively(ExtMap context) throws IOException {
        String pass1 = new String(System.console().readPassword("Password:"));
        String pass2 = new String(System.console().readPassword("Reenter password:"));
        if (!Objects.equals(pass1, pass2)) {
            context.put(ContextKeys.EXIT_STATUS, GENERAL_ERROR);
            addContextMessage(context, true, "Passwords don't match!");
            return null;
        } else {
            return pass1;
        }
    }

    private static void putUserResetPassParams(ExtMap context, Map<String, Object> args) throws IOException {
        String pass;
        String password = null;
        try {
            pass = (String) args.get("password");
            String[] passwords = pass.split(":", 2);
            ExtMap userParams = new ExtMap();
            switch (passwords[0]) {
                case "pass":
                    password = passwords[1];
                    break;
                case "env":
                    password = System.getenv(passwords[1]);
                    break;
                case "file":
                    password = readFile(passwords[1]);
                    break;
                case "interactive":
                    password = readPasswordInteractively(context);
                    break;
                case "none":
                    userParams.put(Schema.UserKeys.NOPASS, true);
                    break;
            }

            userParams.mput(Schema.UserIdentifiers.USERNAME, context.get(ContextKeys.POSITIONAL))
            .mput(Schema.UserKeys.PASSWORD, password)
            .mput(
                Schema.UserKeys.PASSWORD_VALID_TO,
                args.get("password-valid-to") != null ?
                    DateUtils.fromISO((String) args.get("password-valid-to")) :
                    null
            )
            .mput(
                Schema.UserKeys.FORCE_PASSWORD,
                args.get("force")
            );
            context.mput(
                Schema.InvokeKeys.ENTITY_KEYS,
                userParams
                );
        } catch (ParseException e) {
            addContextMessage(context, true, e.getMessage());
            context.mput(ContextKeys.EXIT_STATUS, ARGUMENT_PARSING_ERROR);;
        }
    }

    private static ExtMap getGroupKeys(Map<String, Object> args, String name) {
        return new ExtMap().mput(
            Schema.GroupIdentifiers.NAME,
            name
        ).mput(
            Schema.GroupKeys.UUID,
            args.get("id") // throw an exception if needed
        ).mput(
            Schema.SharedKeys.ATTRIBUTES,
            createAttributes(args)
        ).mput(
            Schema.GroupKeys.NEW_NAME,
            args.get("new-name")
        );
    }

    private static String newLine(String message) {
        return message.endsWith("\n")? message: message + "\n";
    }


    @SuppressWarnings("unchecked")
    private static Boolean getDefFlag(Map<String, Object> input, String name, Boolean def) {
        Boolean ret = null;
        if (input.get("flag") != null) {

            for (String flag: (List<String>) input.get("flag")) {
                if (flag.substring(1).equals(name)) {
                    ret = flag.startsWith("+");
                }
            }
        }
        return ret!= null? ret: def;
    }

    private static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, Charset.forName("UTF-8"));
}

    public static void getGroup(ExtMap context) {
        context.put(
            ContextKeys.SEARCH_FILTER,
            Formatter.format(
                "{} = {}",
                Schema.SEARCH_KEYS.get(Authz.GroupRecord.NAME),
                Formatter.escapeString(context.get(ContextKeys.POSITIONAL, String.class))
            )
        );
        context.mput(Global.InvokeKeys.SEARCH_CONTEXT,
            new ExtMap().mput(Global.SearchContext.PAGE_SIZE, 1)
            .mput(Global.SearchContext.IS_PRINCIPAL, false)
            .mput(Global.SearchContext.RECURSIVE, false)
            .mput(Global.SearchContext.WITH_GROUPS, true)
            .mput(Global.SearchContext.ALL_ATTRIBUTES, true)
        );

        commands.get("_search").invoke(context);
        if (
            context.get(ContextKeys.SEARCH_RESULT) == null ||
            context.get(ContextKeys.SEARCH_RESULT, Collection.class).size() == 0
        ) {
            addContextMessage(context, true, Formatter.format(
                "group {} not found",
                context.get(ContextKeys.POSITIONAL)
            ));
            context.put(ContextKeys.EXIT_STATUS, NOT_FOUND);
        }
    }
}
