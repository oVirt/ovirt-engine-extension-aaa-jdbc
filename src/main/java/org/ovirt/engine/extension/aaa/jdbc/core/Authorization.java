package org.ovirt.engine.extension.aaa.jdbc.core;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;

public class Authorization implements Observer {
    private ExtMap settings;
    private final DataSource ds;
    private AtomicLong nextOpaque = new AtomicLong(1);
    private Map<String, Sql.Cursor<Collection<ExtMap>>> openSelects = new HashMap<>();

    public Authorization(DataSource ds) {
        this.ds = ds;
    }

    @SuppressWarnings("unchecked")
    public String openQuery(
        String filter,
        ExtMap searchContext
    ) throws SQLException {
        String opaque = Long.toString(nextOpaque.getAndIncrement());

        openSelects.put(
            opaque,
            Schema.get(
                new ExtMap().mput(Schema.InvokeKeys.ENTITY, Schema.Entities.CURSOR)
                    .mput(
                        Schema.InvokeKeys.ENTITY_KEYS, new ExtMap().mput(Schema.CursorKeys.FILTER, filter)
                    ).mput(Global.InvokeKeys.SEARCH_CONTEXT, searchContext)
                    .mput(Schema.InvokeKeys.DATA_SOURCE, ds)
            ).get(
                Schema.InvokeKeys.CURSOR_RESULT,
                Sql.Cursor.class
            )
        );
        return opaque;
    }

    @SuppressWarnings("unchecked")
    public Collection<ExtMap> executeQuery(String opaque, Integer pageSize) throws SQLException {

        return Schema.get(
            new ExtMap().mput(Schema.InvokeKeys.ENTITY, Schema.Entities.SEARCH_PAGE)
            .mput(Schema.InvokeKeys.CURSOR_RESULT, openSelects.get(opaque))
            .mput(Global.InvokeKeys.SEARCH_CONTEXT, new ExtMap().mput(
                Global.SearchContext.PAGE_SIZE,
                Math.min(settings.get(Schema.Settings.MAX_PAGE_SIZE, Integer.class), pageSize))
            )
        ).get(
            Schema.InvokeKeys.SEARCH_PAGE_RESULT,
            Collection.class
        );
    }

    public void closeQuery(String opaque) {
        Sql.Cursor<Collection<ExtMap>> remove = openSelects.remove(opaque);
        remove.close();
    }

    public Collection<ExtMap> getResults(String filter, ExtMap searchContext) throws SQLException{
        String opaque = null;
        Collection<ExtMap> principals;
        try {
            opaque = openQuery(filter, searchContext);
            principals = executeQuery(
                opaque,
                searchContext.get(Global.SearchContext.PAGE_SIZE, Integer.class)
            );
            return principals;
        } finally {
            if (opaque!= null) {
                closeQuery(opaque);
            }
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        this.settings = (ExtMap)arg;
    }
}
