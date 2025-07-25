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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class Sql {
    public static class ModificationTypes {
        public static final int INSERT = 0;
        public static final int UPDATE = 1;
        public static final int DELETE = 2;
    }

    public static class ModificationContext {
        public static final ExtKey GENERATED_IDS = new ExtKey("AAA_JDBC_GENERATED_IDS", List/*<Integer>*/.class, "6a587302-bc8d-42fd-8708-30915012f271");
        public static final ExtKey LAST_STATEMENT_ROWS = new ExtKey("AAA_JDBC_LAST_STATEMENT_ROWS", Integer.class, "c1ad321c-ec84-43e4-a8ff-a4f34d1027e7");
    }


    private static final Map<Integer,String> templates = new HashMap<>();
    static {
        templates.put(ModificationTypes.INSERT, "INSERT INTO {}({}) VALUES ({})");
        templates.put(ModificationTypes.UPDATE, "UPDATE {} SET {} {};");
        templates.put(ModificationTypes.DELETE, "DELETE FROM {} {}");
    }

    private static final Logger LOG = LoggerFactory.getLogger(Sql.class);

    public static class Template {
        private final int op;
        private final String table;
        private final List<String> columns = new ArrayList<>();
        private final List<String> values = new ArrayList<>();
        private String where = null;

        public Template(int op, String table) {
            this.op = op;
            this.table = table;
        }

        /**
         * setX invocations are ignored if:
         *
         * <ul>
         * <li>this.op == DELETE statements</li>
         * <li>value == null</li>
         * </ul>
         *
         * @param column column name
         * @param value column value
         * @return this
         */
        public Template setRaw(String column, String value) {
            if (where != null) {
                throw new IllegalStateException();
            }
            if (value != null) {
                columns.add(column);
                values.add(value.toString());
            }
            return this;
        }

        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setRaw(String, String)
         */
        public Template setIncrement(String column) {
            return setRaw(column, "(" + column + "+1)");
        }


        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setRaw(String, String)
         */
        public Template setString(String column, String value) {
            if (value != null) {
                setRaw(
                    column,
                    Formatter.escapeString(value)
                );
            }
            return this;
        }

        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setRaw(String, String)
         */
        public Template setInteger(String column, Integer value) {
            if (value != null) {
                columns.add(column);
                values.add(value.toString());
            }
            return this;
        }

        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setString(String, String)
         */
        public Template setLong(String column, Long value){
            if (value != null) {
                columns.add(column);
                values.add(value.toString());
            }
            return this;
        }

        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setString(String, String)
         */
        public Template setTimestamp(String column, Long value){
            if (value != null) {
                columns.add(column);
                values.add(DateUtils.toTimestamp(value));
            }
            return this;
        }

        /**
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.Template#setString(String, String)
         */
        public Template setBool(String column, Boolean value) {
            if (value != null) {
                columns.add(column);
                values.add(value ? "1" : "0");
            }
            return this;
        }

        /**
         * where invocations are ignored if this.op == INSERT statements
         *
         * @param where sql where clause e.g "name = bob"
         * @return this
         */
        public Template where(String where) {
            if (this.where != null) {
                throw new IllegalStateException();
            }
            this.where = where;
            return this;
        }

        public String asSql(){
            List<String> arguments = new ArrayList<>();
            arguments.add(table);
            if (this.op == ModificationTypes.INSERT) {
                arguments.add(StringUtils.join(columns, ", "));
                arguments.add(StringUtils.join(values, ", "));
            } else {
                if (this.op == ModificationTypes.UPDATE){
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < columns.size(); i++) {
                        sb.append(columns.get(i)).append("=").append(values.get(i)).append(",");
                    }
                    if (sb.length() > 0) {
                        sb.setLength(sb.length() - 1);
                    }
                    arguments.add(sb.toString());
                }
                arguments.add(
                    StringUtils.isEmpty(where)?
                        "":
                        "WHERE " + where
                );
            }
            return MessageFormatter.arrayFormat(
                templates.get(op),
                arguments.toArray()
            ).getMessage();
        }
    }

    public static class Modification {

        private final List<String> statements;

        public Modification(String statement) {
            this(Collections.singletonList(statement));
        }

        public Modification(List<String> statements) {
            this.statements = statements;
        }
        public ExtMap execute(DataSource dataSource) throws SQLException {
            return execute(dataSource, true);
        }

        public ExtMap execute(DataSource dataSource, boolean commit) throws SQLException {
            return execute(dataSource.getConnection(), commit);
        }

        /**
         * @param conn a connection to operate on
         * @param commit if true:
         *               1) commit after executing statements
         *               2) attempt to rollback on exception,
         *               3) close when done
         * @return a context containing data on the transaction.
         *
         * @see org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql.ModificationContext
         *
         * @throws SQLException
         */
        public ExtMap execute(Connection conn, boolean commit) throws SQLException {
            List<Integer> generatedIds = new LinkedList<>();
            int lastStatementRet = -1;
            try {
                conn.setAutoCommit(false);
                try (java.sql.Statement statement = conn.createStatement()) {
                    for (String template : statements) {
                        LOG.trace("executing statements: {}", template);
                        lastStatementRet = statement.executeUpdate(template, java.sql.Statement.RETURN_GENERATED_KEYS);

                        ResultSet rs = statement.getGeneratedKeys();
                        if (rs.next()) {
                            generatedIds.add(rs.getInt(1));
                        }
                    }
                    if (commit) {
                        LOG.trace("committing transaction.");
                        conn.commit();
                    }
                }
            } catch (SQLException e) {
                if (commit) {
                    conn.rollback();
                }
                throw e;
            } finally {
                if (commit) {
                    closeQuietly(conn);
                }
            }

            return new ExtMap().mput(
                ModificationContext.GENERATED_IDS,
                generatedIds
            ).mput(
                ModificationContext.LAST_STATEMENT_ROWS,
                lastStatementRet
            );
        }
    }

    public static class Query {
        private String query;

        public Query(String query) {
            this.query = query;
        }

        public <T> T asResults(DataSource dataSource, final ResultsResolver<T> resolver) throws SQLException {
            return asResults(dataSource, resolver, new ExtMap());
        }



        public <T> T asResults(
            DataSource dataSource,
            final ResultsResolver<T> resolver,
            ExtMap context
        ) throws SQLException {
            T resolved;
            Cursor<T> q = null;
            try {
                q = new Query(query).asCursor(dataSource.getConnection(), resolver);
                resolved = resolver.resolve(q.resultSet, context);
            } finally {
                closeQuietly(q);
            }
            return resolved;
        }

        public <T> T asResults(
            Connection conn,
            final ResultsResolver<T> resolver,
            ExtMap context
        ) throws SQLException {
            T resolved;
            Cursor<T> q = null;
            try {
                q = new Query(query).asCursor(conn, resolver);
                resolved = resolver.resolve(q.resultSet, context);
            } finally {
                closeQuietly(q.statement, q.resultSet);
            }
            return resolved;
        }

        public String asString(Connection conn, final String columnName) throws SQLException {
            return asResults(
                conn,
                new ResultsResolver<String>() {
                    @Override
                    public String resolve(ResultSet rs, ExtMap context) throws SQLException {
                        String ret = null;
                        if (rs.next()) {
                            ret = rs.getString(columnName);
                        }
                        return ret;
                    }
                },
                new ExtMap()
            );
        }

        public Integer asInteger(Connection conn, final String columnName) throws SQLException {
            return asResults(
                conn,
                new ResultsResolver<Integer>() {
                    @Override
                    public Integer resolve(ResultSet rs, ExtMap context) throws SQLException {
                        Integer ret = null;
                        if (rs.next()) {
                            ret = rs.getInt(columnName);
                        }
                        return ret;
                    }
                },
                new ExtMap()
            );
        }

        public Integer asInteger(DataSource dataSource, final String columnName) throws SQLException {
            return asResults(
                dataSource,
                new ResultsResolver<Integer>() {
                    @Override
                    public Integer resolve(ResultSet rs, ExtMap context) throws SQLException {
                        Integer ret = null;
                        if (rs.next()) {
                            ret = rs.getInt(columnName);
                        }
                        return ret;
                    }
                },
                new ExtMap()
            );
        }

        public <T> Cursor<T> asCursor(Connection conn, ResultsResolver<T> resolver) throws SQLException{
            return asCursor(conn, resolver, false, new ExtMap());
        }

        public <T> Cursor<T> asCursor(
            Connection conn,
            ResultsResolver<T> resolver,
            boolean scrollable,
            ExtMap ctx
        ) throws SQLException{
            LOG.trace("cursor for: {}", query);
            ResultSet resultSet = null;
            java.sql.Statement statement = null;
            try {
                statement = conn.createStatement(
                    scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
                );
                resultSet = statement.executeQuery(query);
                return new Cursor<>(conn, statement, resultSet, ctx, resolver);
            } catch (Exception ex){
                closeQuietly(conn, statement, resultSet);
                throw ex;
            }
        }
    }

    public interface ResultsResolver<T> {
        T resolve(ResultSet resultSet, ExtMap context) throws SQLException;
    }

    public static class Cursor<T> implements AutoCloseable {
        private final Connection connection;
        private final java.sql.Statement statement;
        public final ResultSet resultSet;
        private final ExtMap context;
        private final ResultsResolver<T> resolver;

        public Cursor(
            Connection connection,
            java.sql.Statement statement,
            ResultSet resultSet,
            ExtMap context,
            ResultsResolver<T> resolver
        ) {
            this.connection = connection;
            this.statement = statement;
            this.resultSet = resultSet;
            this.context = context;
            this.resolver = resolver;
        }

        @Override
        public void close() {
            closeQuietly(resultSet, statement, connection);
        }

        public T resolve(ExtMap queryCtx) throws SQLException {
            context.putAll(queryCtx);
            return resolver.resolve(resultSet, context);
        }
    }

    public static void closeQuietly(AutoCloseable... closables) {
        for (AutoCloseable closable : closables) {
            if (closable != null) {
                try {
                    closable.close();
                } catch (Exception e) {
                        LOG.warn("Cannot close AutoCloseable {}, ignoring.", closable.getClass().getName());
                        LOG.debug("Exception", e);
                }
            }
        }
    }

}
