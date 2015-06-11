package org.ovirt.engine.extension.aaa.jdbc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtils {

    public static final int MILLIS_IN_MINUTE = 1000 * 60;
    public static final int MILLIS_IN_HOUR = MILLIS_IN_MINUTE * 60;
    public static final int MILLIS_IN_DAY = MILLIS_IN_HOUR * 24;
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX");

    public static String toISO(long time) {
        Date date = new Date(time);
        return ISO.format(date);
    }

    public static long fromISO(String iso) throws ParseException {
        return ISO.parse(iso).getTime();
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
