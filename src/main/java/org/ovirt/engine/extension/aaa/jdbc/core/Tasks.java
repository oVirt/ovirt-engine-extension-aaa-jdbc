package org.ovirt.engine.extension.aaa.jdbc.core;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Observable;
import java.util.Observer;
import javax.sql.DataSource;

import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.Formatter;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tasks extends Observable {
    private static final Logger LOG = LoggerFactory.getLogger(Tasks.class);
    private static final long REFRESH_SETTINGS_INTERVAL_MINUTES_DEFAULT = 60; // required when settings not yet loaded.
    private final DataSource ds;
    long lastSettings = Global.SETTINGS_SPECIAL;


    long lastHouseKeeping = Global.SETTINGS_SPECIAL;

    //    private static final List<Task> TASKS;
    private ExtMap settings = new ExtMap();

    public Tasks(DataSource ds, Observer ... observers) {
        for (Observer observer: observers) {
            addObserver(observer);
        }
        this.ds = ds;
    }

    public void execute() throws SQLException {
        long now = System.currentTimeMillis();
        if (
            isDue(
                lastSettings,
                settings.get(
                    Schema.Settings.REFRESH_SETTINGS_INTERVAL_MINUTES,
                    Long.class,
                    REFRESH_SETTINGS_INTERVAL_MINUTES_DEFAULT
                ) * DateUtils.MILLIS_IN_MINUTE,
                now
            )
        ) {
            settings =
                Schema.get(
                    new ExtMap().mput(Schema.InvokeKeys.ENTITY, Schema.Entities.SETTINGS)
                    .mput(Schema.InvokeKeys.DATA_SOURCE, ds)
                ).get(
                    Schema.InvokeKeys.SETTINGS_RESULT,
                    ExtMap.class
                );
            setChanged();
            notifyObservers(settings);
            lastSettings = now;
        }
        if (
            isDue(
                lastHouseKeeping,
                settings.get(Schema.Settings.HOUSE_KEEPING_INTERVAL_HOURS, Long.class) * DateUtils.MILLIS_IN_HOUR,
                now
            )
        ) {
            Integer old = settings.get(Schema.Settings.FAILED_LOGINS_OLD_DAYS, Integer.class);
            if (old != Global.SETTINGS_SPECIAL) {
                long time = DateUtils.add(
                    System.currentTimeMillis(),
                    Calendar.DAY_OF_YEAR,
                    -old
                );
                LOG.info(
                    "(house keeping) deleting failed logins prior to {}.",
                    DateUtils.toISO(time)
                );
                try {
                    new Sql.Modification(
                        new Sql.Template(
                            Sql.ModificationTypes.DELETE,
                            "failed_logins"
                        ).where(
                            Formatter.format("minute_start < {}", DateUtils.toTimestamp(time))
                        ).asSql()
                    ).execute(ds);
                } catch (SQLException e) {
                    LOG.warn("exception deleting old failed logins. ignoring.", e);
                }
            }
            lastHouseKeeping = now;
        }
    }

    private boolean isDue(long lastMillis, long minIntervalMillis, long now) {
        return (
            lastMillis == Global.SETTINGS_SPECIAL ||
            (
                minIntervalMillis != Global.SETTINGS_SPECIAL &&
                (now - lastMillis) > minIntervalMillis
            )
        );
    }
}
