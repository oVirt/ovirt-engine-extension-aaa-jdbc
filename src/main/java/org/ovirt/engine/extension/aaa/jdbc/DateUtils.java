package org.ovirt.engine.extension.aaa.jdbc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {

    public static final int MILLIS_IN_MINUTE = 1000 * 60;
    public static final int MILLIS_IN_HOUR = MILLIS_IN_MINUTE * 60;
    public static final int MILLIS_IN_DAY = MILLIS_IN_HOUR * 24;
    private static final SimpleDateFormat ISO;

    static {
        ISO = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX");
        ISO.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String toISO(long time) {
        return ISO.format(new Date(time));
    }

    public static long fromISO(String iso) throws ParseException {
        return ISO.parse(iso).getTime();
    }

    /**
     * PostgreSQL to_timestamp() doesn't support timezone specifications, so this is the only way how to set correct
     * timestamp with timezone using string (as our current DAOs uses only strings to create SQL).
     */
    public static String toTimestamp(long time) {
        return String.format(
            "to_timestamp(%d::double precision / 1000) AT TIME ZONE 'UTC'",
            time
        );
    }

    public static Calendar getUtcCalendar() {
        return Calendar.getInstance(
            TimeZone.getTimeZone("UTC"),
            Locale.US
        );
    }

    public static long getLastSunday(long date) {
        Calendar login = Calendar.getInstance();
        login.setTime(new Date(date));

        GregorianCalendar sunday = new GregorianCalendar(
            login.get(Calendar.YEAR),
            login.get(Calendar.MONTH),
            login.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        );
        while (sunday.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            sunday.add(Calendar.DAY_OF_WEEK, -1);
        }
        return sunday.getTime().getTime();
    }

    public static long add(long date, int field, int amount) {
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTime(new Date(date));
        cal.add(field, amount);
        return cal.getTime().getTime();
    }
}
