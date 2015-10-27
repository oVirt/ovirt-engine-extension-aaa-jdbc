package org.ovirt.engine.extension.aaa.jdbc.binding.cli.command;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.Cli;
import org.ovirt.engine.extension.aaa.jdbc.binding.cli.ContextKeys;
import org.ovirt.engine.extension.aaa.jdbc.core.Schema;
import org.ovirt.engine.extension.aaa.jdbc.core.Schema.InvokeKeys;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;

public class GroupManageShowCommand extends Command {

    @Override
    public String getName() {
        return "root-group-manage-show";
    }

    @Override
    public boolean entityNameExpected() {
        return true; // group name expected
    }

    @Override
    public void invoke(ExtMap context, Map<String, Object> args) {
        try {
            //Load Group fields into a map.
            ExtMap resultsMap = getGroup(context);
            if (resultsMap != null) {
                //Get group name, uuid and database-id (the latter is necessary for joins).
                String groupName = (String) resultsMap.get(Authz.GroupRecord.NAME);
                String groupUuid = (String) resultsMap.get(Authz.GroupRecord.ID);
                Integer groupDbId = (Integer) resultsMap.get(Schema.GroupKeys.DB_ID);

                //get users and groups associated with this group.
                DataSource dataSource = context.get(InvokeKeys.DATA_SOURCE, DataSource.class);
                List<String> groups = getMemberGroups(groupDbId, dataSource);
                List<String> users = getMemberUsers(groupDbId, dataSource);

                //display the results
                show(context, groupName, groupUuid, groups, users);
            }
        } catch (SQLException e) {
            context.put(ContextKeys.EXIT_STATUS, Cli.SQL_ERROR);
            addContextMessage(context, true, e.getMessage());
            context.get(ContextKeys.THROWABLES, Collection.class).add(e);
        }
    }

    /**
     * Fetches the group from the database and fills its fields in a map.
     */
    private ExtMap getGroup(ExtMap context) {
        ExtMap resultsMap = null;
        Cli.getGroup(context);
        if (context.get(ContextKeys.SEARCH_RESULT) != null && !context.get(ContextKeys.SEARCH_RESULT, Collection.class).isEmpty()) {
            resultsMap = (ExtMap) (context.get(ContextKeys.SEARCH_RESULT, Collection.class).iterator().next());
        }
        return resultsMap;
    }


    /**
     * Gets users which are members of this group.
     */
    private List<String> getMemberUsers(Integer groupId, DataSource dataSource) throws SQLException {
        String sqlQuery = "select u.name as name from users u, user_groups ug where ug.in_group_id=" + groupId + " and u.id=ug.user_id";
        List<String> results = new Sql.Query(sqlQuery).asResults(dataSource, NameListResolver.instance);
        return results;
    }


    /**
     * Gets groups which are members of this group.
     */
    private List<String> getMemberGroups(Integer groupId, DataSource dataSource) throws SQLException {
        String sqlQuery = "select g.name as name from groups g, group_groups gg where gg.in_group_id=" + groupId + " and g.id=gg.group_id";
        List<String> results = new Sql.Query(sqlQuery).asResults(dataSource, NameListResolver.instance);
        return results;
    }


    /**
     * Displays the output of the command in the following format:
     *
     *   Group some_group(cedecfe3-846c-4428-acb6-e2a01ffb4951) members:
     *     user: user_1
     *     user: user_2
     *     group: group_1
     *     group: group_2
     *
     * If the group has no members, the display is:
     *
     *   Group some_group(cedecfe3-846c-4428-acb6-e2a01ffb4951) is empty.
     */
    private void show(ExtMap context, String groupName, String groupUuid, List<String> memberGroups, List<String> memberUsers) {
        StringBuilder builder = new StringBuilder();
        builder.append("Group: ").append(groupName).append("(").append(groupUuid).append(")");
        if (memberGroups.isEmpty() && memberUsers.isEmpty()) {
            builder.append(" has no members.");
        } else {
            builder.append(" members:\n");
            for (String user : memberUsers) {
                builder.append("  User: ").append(user).append("\n");
            }
            for (String group : memberGroups) {
                builder.append("  Group: ").append(group).append("\n");
            }
        }
        String output = builder.toString().trim();
        addContextMessage(context, false, output);
    }

    /**
     * A Resolver that knows how to handle such results:
     *    name=a,
     *    name=b,
     *    name-c
     *    .
     *    .
     *    .
     * (both queries - for users and for groups - return the results in this format).
     */
    private static class NameListResolver implements Sql.ResultsResolver<List<String>>{
        static NameListResolver instance = new NameListResolver();
        @Override
        public List<String> resolve(ResultSet resultSet, ExtMap context) throws SQLException {
            List<String> results = new LinkedList<>();
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                results.add(name);
            }
            return results;
        }
    }
}
