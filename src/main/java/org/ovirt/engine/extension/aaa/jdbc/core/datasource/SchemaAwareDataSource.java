package org.ovirt.engine.extension.aaa.jdbc.core.datasource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;

import org.ovirt.engine.extension.aaa.jdbc.Formatter;

/**
 * DataSource proxy class that sets custom schema as default for all connections
 *
 * TODO: Move custom default schema setting into JDBC URL when we will use PostgreSQL 9.4+ and remove the class
 */
public class SchemaAwareDataSource implements DataSource {
    private final DataSource dataSource;
    private final String customSchema;

    public SchemaAwareDataSource(DataSource dataSource, String customSchema) {
        this.dataSource = dataSource;
        this.customSchema = customSchema;
    }

    private Connection applyCustomSchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(
                String.format(
                    "SET search_path TO %s",
                    Formatter.escapeString(customSchema)
                )
            );
        }
        return connection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyCustomSchema(dataSource.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyCustomSchema(dataSource.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter printWriter) throws SQLException {
        dataSource.setLogWriter(printWriter);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }
}
