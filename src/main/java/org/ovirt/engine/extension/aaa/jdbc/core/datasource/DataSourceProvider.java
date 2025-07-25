package org.ovirt.engine.extension.aaa.jdbc.core.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DriverManager;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;

/**
 * Read some static jdbc data configuration.
 *
 * If a JNDI name has been configured it will be used.
 *
 * Otherwise, a proxy is returned.
 *
 * The proxy always calls driver manager and used parameters to get a connection.
 *
 */
public class DataSourceProvider {

    private static final String JNDI = "config.datasource.jndi";

    private static final String JDBC_DRIVER = "config.datasource.jdbcdriver";

    private static final String JDBC_URL = "config.datasource.jdbcurl";

    private static final String DATABASE_USER = "config.datasource.dbuser";

    private static final String DATABASE_PASSWORD = "config.datasource.dbpassword";

    public static final String SCHEMA_NAME = "config.datasource.schemaname";

    public static final String DEFAULT_SCHEMA_NAME = "public";

    private static DataSource dataSource;

    public DataSourceProvider(Properties configuration) {
        String datasourceJndi = configuration.getProperty(JNDI);
        String schemaName = configuration.getProperty(SCHEMA_NAME);
        if (StringUtils.isBlank(schemaName)) {
            schemaName = DEFAULT_SCHEMA_NAME;
        }

        DataSource ds;
        if (!StringUtils.isEmpty(datasourceJndi)) {
            ds = getDataSourceFromJNDI(datasourceJndi);
        } else {
            ds = createDataSource(
                    configuration.getProperty(JDBC_DRIVER),
                    configuration.getProperty(JDBC_URL),
                    configuration.getProperty(DATABASE_USER),
                    configuration.getProperty(DATABASE_PASSWORD)
            );
        }
        dataSource = new SchemaAwareDataSource(ds, schemaName);
    }

    private DataSource createDataSource(
            final String jdbcDriver,
            final String jdbcUrl,
            final String dbUser,
            final String dbPassword) {
        try {
            Class.forName(jdbcDriver);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Failed to load JDBC_DRIVER: " + jdbcDriver);
        }
        return (DataSource) Proxy.newProxyInstance(
            this.getClass().getClassLoader(),
            new Class<?>[] { DataSource.class },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("toString")) {
                        return "DataSource";
                    }
                    if (!method.getName().equals("getConnection")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                }
            }
        );
    }

    private DataSource getDataSourceFromJNDI(String property) {

        DataSource dataSource;
        try {
            InitialContext context = new InitialContext();
            dataSource = (DataSource) context.lookup(property);
        } catch (NamingException e) {
            throw new IllegalArgumentException("Could not lookup datasource.", e);
        }
        return dataSource;
    }

    public DataSource provide() {
        return dataSource;
    }
}
