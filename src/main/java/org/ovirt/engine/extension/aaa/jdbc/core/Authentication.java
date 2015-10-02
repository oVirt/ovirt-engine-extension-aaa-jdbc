package org.ovirt.engine.extension.aaa.jdbc.core;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.extension.aaa.jdbc.DateUtils;
import org.ovirt.engine.extension.aaa.jdbc.Global;
import org.ovirt.engine.extension.aaa.jdbc.core.datasource.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Authentication implements Observer {
    public static class AuthRecord {
        public final String principal; // M
        public final long validTo; // M

        public AuthRecord(String principal, long validTo) {
            this.principal = principal;
            this.validTo = validTo;
        }

        @Override
        public String toString() {
            return "AuthRecord{" +
                "principal='" + principal + '\'' +
                ", validTo=" + DateUtils.toISO(validTo) +
                '}';
        }
    }

    public static class AuthResponse {
        public final AuthRecord authRecord;
        public final String principal;
        public final int result; // Only result is mandatory
        public final String dailyMsg;
        public final String baseMsg;

        private final Schema.User user;

        private AuthResponse(
            AuthRecord authRecord,
            Schema.User user,
            int result,
            String dailyMsg,
            String baseMsg
        ) {
            this.authRecord = authRecord;
            this.user = user;
            this.principal = (user == null ? null : user.getName());
            this.result = result;
            this.dailyMsg = dailyMsg;
            this.baseMsg = baseMsg;
        }

        public static AuthResponse negative(int result, Schema.User user, String baseMsg){
            return new AuthResponse(null, user, result, null, baseMsg);
        }

        public static AuthResponse negative(int result){
            return new AuthResponse(null, null, result, null, null);
        }

        public static AuthResponse positive(AuthRecord authRecord, Schema.User user, String dailyMsg){
            return new AuthResponse(authRecord, user, Authn.AuthResult.SUCCESS, dailyMsg, null);
        }

        public static AuthResponse positive() {
            return new AuthResponse(null, null, Authn.AuthResult.SUCCESS, null, null);
        }

        @Override
        public String toString() {
            return "AuthResponse{" +
                "authRecord= " +
                ((authRecord != null)? authRecord.toString(): null) +
                ", principal='" + principal + '\'' +
                ", result=" + result +
                ", dailyMsg='" + dailyMsg + '\'' +
                ", baseMsg='" + baseMsg + '\'' +
                '}';
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);
    public static final int MIN_SLEEP = 100;


    static final private Pattern COMPLEXITY_PATTERN = Pattern.compile(
        "(?<name>[^:]*)" +
        ":" +
        "(" +
            "(chars=(?<chars>.*?)::)|" +
            "(min=(?<min>.*?)::)" +
        ")*"
    );


    private final DataSource ds;
    private ExtMap settings;
    private Complexity complexity;
    public Authentication(DataSource ds) {
        this.ds = ds;
    }

    public AuthResponse doAuth(
        String subject,
        String credentials,
        boolean credChange,
        String newCredentials
    ) throws GeneralSecurityException, SQLException, IOException {
        long loginTime = System.currentTimeMillis(); // start the clock
        AuthResponse response = null;

        LOG.debug("Authenticating subject:{} login time:{}", subject, DateUtils.toISO(loginTime));
        try {
            response = authenticate(
                subject,
                credentials,
                loginTime
            );
            if (
                credChange &&
                (
                    response.result == Authn.AuthResult.SUCCESS ||
                    (
                        response.result == Authn.AuthResult.CREDENTIALS_EXPIRED &&
                        settings.get(Schema.Settings.ALLOW_EXPIRED_PASSWORD_CHANGE, Boolean.class)
                    )
                )
            ) {
                AuthResponse credChangeResponse = checkCredChange(response.user, newCredentials);
                if (credChangeResponse.result == Authn.AuthResult.SUCCESS) {
                    updateUser(
                        new ExtMap().mput(Schema.UserIdentifiers.USER_ID, response.user.getId())
                        .mput(Schema.UserKeys.PASSWORD,
                            EnvelopePBE.encode(
                                settings.get(Schema.Settings.PBE_ALGORITHM, String.class),
                                settings.get(Schema.Settings.PBE_KEY_SIZE, Integer.class),
                                settings.get(Schema.Settings.PBE_ITERATIONS, Integer.class),
                                null,
                                newCredentials
                            )
                        )
                        .mput(Schema.UserKeys.OLD_PASSWORD, response.user.getPassword())
                        .mput(Schema.UserKeys.PASSWORD_VALID_TO,
                            DateUtils.add(
                                loginTime,
                                Calendar.DATE,
                                settings.get(Schema.Settings.PASSWORD_EXPIRATION_DAYS, Integer.class)
                            )
                        )
                    );
                    response = AuthResponse.positive();
                } else {
                    response = credChangeResponse;
                }
            }
            return response;
        } finally {
            if (response == null || response.result != Authn.AuthResult.SUCCESS) {
                delayResponse(loginTime);
            }
        }
    }

    //never return null
    private AuthResponse authenticate(
        String subject,
        String credentials,
        long loginTime
    ) throws GeneralSecurityException, SQLException, IOException {
        AuthResponse response = null;
        Schema.User user = null;

        if (response == null) {
            user = getUser(subject);
            if (user == null) {
                response = AuthResponse.negative(Authn.AuthResult.GENERAL_ERROR);
            }
        }

        synchronized (subject.intern()) { // principal known
            if (response == null) {
                response = isAuthAllowed(user, loginTime);
                if (
                    response == null &&
                    (
                        user.isNopasswd() ||
                        EnvelopePBE.check(user.getPassword(), credentials)
                    )
                ) {
                    response = AuthResponse.positive(
                        new AuthRecord(subject, getValidTo(user, loginTime)),
                        user,
                        getUserMessages(loginTime, user)
                    );
                }
                if (response == null) {
                    response = AuthResponse.negative(Authn.AuthResult.CREDENTIALS_INCORRECT, user, "credentials incorrect");
                }
            }

            if (user != null) { // update db
                if (response.result == Authn.AuthResult.SUCCESS) {
                    updateUser(
                        new ExtMap().mput(Schema.UserIdentifiers.USER_ID, user.getId())
                        .mput(Schema.UserKeys.SUCCESSFUL_LOGIN, loginTime)
                    );
                } else {
                    updateUser(
                        new ExtMap().mput(Schema.UserIdentifiers.USER_ID, user.getId())
                        .mput(Schema.UserKeys.UNSUCCESSFUL_LOGIN, loginTime)
                    );
                    user = getUser(subject);
                    checkLock(user, loginTime);
                }
            }
        }
        return response;
    }

    private Schema.User getUser(String subject) throws SQLException {
        return
            Schema.get(
                new ExtMap().mput(Schema.InvokeKeys.ENTITY, Schema.Entities.USER)
                .mput(Schema.InvokeKeys.DATA_SOURCE, ds)
                .mput(Schema.InvokeKeys.SETTINGS_RESULT, settings)
                .mput(
                    Schema.InvokeKeys.ENTITY_KEYS,
                    new ExtMap().mput(
                        Schema.UserIdentifiers.USERNAME,
                        subject
                    )
                )
            ).get(
                Schema.InvokeKeys.USER_RESULT,
                Schema.User.class
            );
    }

    private void checkLock(Schema.User user, long loginTime) throws SQLException {
        boolean consecutive;
        if (
            (
                consecutive =
                    user.getConsecutiveFailures() >= settings.get(
                        Schema.Settings.MAX_FAILURES_SINCE_SUCCESS,
                        Integer.class
                    )
            ) ||
            (
                user.countFailuresSince(
                    DateUtils.add(
                        loginTime,
                        Calendar.HOUR,
                        -settings.get(Schema.Settings.INTERVAL_HOURS, Integer.class)
                    )
                ) >= settings.get(Schema.Settings.MAX_FAILURES_PER_INTERVAL, Integer.class)
            )
        ) {
            LOG.info(
                "locking user: {} due to {}",
                user.getName(),
                consecutive ?
                "consecutive failures" :
                "interval failures"
            );

            updateUser(
                new ExtMap().mput(Schema.UserIdentifiers.USER_ID, user.getId())
                    .mput(
                        Schema.UserKeys.UNLOCK_TIME,
                        DateUtils.add(
                            loginTime,
                            Calendar.MINUTE,
                            settings.get(Schema.Settings.LOCK_MINUTES, Integer.class)
                        )
                    )
            );
        }
    }

    private void updateUser(ExtMap updateKeys) throws SQLException {
        Schema.modify(
            new ExtMap().mput(Schema.InvokeKeys.ENTITY, Schema.Entities.USER)
                .mput(Schema.InvokeKeys.MODIFICATION_TYPE, Sql.ModificationTypes.UPDATE)
                .mput(Schema.InvokeKeys.DATA_SOURCE, ds)
                .mput(Schema.InvokeKeys.SETTINGS_RESULT, settings)
                .mput(
                    Schema.InvokeKeys.ENTITY_KEYS,
                    updateKeys
                )
        );
    }

    private AuthResponse isAuthAllowed(Schema.User user, long loginTime)  {
        AuthResponse res = null;
        if (user.isDisabled()) {
            res = AuthResponse.negative(Authn.AuthResult.ACCOUNT_DISABLED, user, "account disabled");
        }

        Integer maxAttempts = settings.get(Schema.Settings.MAX_FAILURES_PER_MINUTE, Integer.class);
        if (
            res == null &&
            maxAttempts != Global.SETTINGS_SPECIAL &&
            user.countFailuresSince(
                DateUtils.add(loginTime, Calendar.MINUTE, -1)
            ) >= maxAttempts
        ) {
            res = AuthResponse.negative(Authn.AuthResult.ACCOUNT_RESTRICTION, user, "too many attempts per minute");
        }
        if (
            res == null &&
            !user.isNopasswd() &&
            loginTime > user.getPasswordValidTo()
        ) {
            res = AuthResponse.negative(Authn.AuthResult.CREDENTIALS_EXPIRED, user, "credentials expired");
        }
        if (
            res == null &&
            (
                user.getValidFrom() > loginTime ||
                user.getValidTo() < loginTime
            )
        ) {
            res = AuthResponse.negative(Authn.AuthResult.ACCOUNT_EXPIRED, user, "account expired");
        }
        if (
            res == null &&
            user.getUnlockTime() > loginTime
        ) {
            res = AuthResponse.negative(Authn.AuthResult.ACCOUNT_LOCKED, user, "account locked");
        }
        if (
            res == null &&
            Schema.User.getLoginAllowed(loginTime, user.getLoginAllowed()) == loginTime
        ) {
            res = AuthResponse.negative(Authn.AuthResult.ACCOUNT_TIME_VIOLATION, user, "account time violation");
        }
        return res;
    }

    private String getUserMessages(long loginTime, Schema.User principal) {
        StringBuilder messages = new StringBuilder();
        String separator = settings.get(Schema.Settings.MESSAGE_SEPARATOR, String.class);
        if (settings.get(Schema.Settings.PRESENT_WELCOME_MESSAGE, Boolean.class)) {
            messages.append(principal.getWelcomeMessage())
            .append(separator);

        }
        if (!StringUtils.isEmpty(settings.get(Schema.Settings.MESSAGE_OF_THE_DAY, String.class))) {
            messages.append(settings.get(Schema.Settings.MESSAGE_OF_THE_DAY, String.class))
            .append(separator);
        }
        if (
            settings.get(Schema.Settings.PASSWORD_EXPIRATION_NOTICE_DAYS, Integer.class)
            != Global.SETTINGS_SPECIAL
        ) {
            String expirationMessage =
                principal.getExpirationMessage(
                    loginTime,
                    settings.get(
                        Schema.Settings.PASSWORD_EXPIRATION_NOTICE_DAYS,
                        Integer.class
                    )
            );
            if (!StringUtils.isEmpty(expirationMessage)) {
                messages.append(expirationMessage)
                .append(separator);
            }
        }
        if (messages.length() > 0) {
            messages.setLength(messages.length() - separator.length());
        }
        return messages.toString();
    }

    private void delayResponse(long loginStart) {
        long endTime = DateUtils.add(
            loginStart,
            Calendar.SECOND,
            settings.get(
                Schema.Settings.MINIMUM_RESPONSE_SECONDS,
                Integer.class
            )
        );

        long interval;

        while ((interval = (endTime - System.currentTimeMillis())) > 0) {
            try {
                Thread.sleep(Math.max(MIN_SLEEP, interval));
                break;
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while delaying response, reentering.", e);
            }
        }
    }

    private long getValidTo(Schema.User user, long loginTime) {
        List<Long> timeConstraints = new ArrayList<>(3);
        timeConstraints.add(Schema.User.getLoginAllowed(loginTime, user.getLoginAllowed()));
        timeConstraints.add(user.getValidTo());

        Integer loginMinutes = settings.get(Schema.Settings.MAX_LOGIN_MINUTES, Integer.class);
        if (loginMinutes != Global.SETTINGS_SPECIAL) {
            Calendar globalMax = DateUtils.getUtcCalendar();
            globalMax.setTimeInMillis(loginTime);
            globalMax.add(Calendar.MINUTE, loginMinutes);
            timeConstraints.add(globalMax.getTimeInMillis());
        }
        Collections.sort(timeConstraints);
        return timeConstraints.get(0);
    }

    public AuthResponse checkCredChange(
        Schema.User user,
        String newCredentials
    ) throws GeneralSecurityException, IOException {
        AuthResponse response = null;
        if (newCredentials.length() < settings.get(Schema.Settings.MIN_LENGTH, Integer.class)) {
            response = AuthResponse.negative(
                Authn.AuthResult.GENERAL_ERROR,
                user,
                "new password too short"
            );

        }
        if (response == null && !complexity.check(newCredentials)) {
            response = AuthResponse.negative(
                Authn.AuthResult.GENERAL_ERROR,
                user,
                complexity.getUsage()
            );
        }
        if (response == null && !user.getPassword().equals("") && EnvelopePBE.check(user.getPassword(), newCredentials)) {
            response = AuthResponse.negative(Authn.AuthResult.GENERAL_ERROR, user, "new password already used");
        }
        if (response == null) {
            for (Schema.User.PasswordHistory oldPassword : user.getOldPasswords()) {
                if (!user.getPassword().equals("") && EnvelopePBE.check(oldPassword.password, newCredentials)) {
                    response = AuthResponse.negative(Authn.AuthResult.GENERAL_ERROR, user, "new password already used");
                }
            }
        }
        if (response == null) {
            response = AuthResponse.positive();
        }
        return response;
    }

    @Override
    public void update(Observable o, Object arg) {
        this.settings = (ExtMap)arg;
        Matcher m = COMPLEXITY_PATTERN.matcher(settings.get(Schema.Settings.PASSWORD_COMPLEXITY, String.class));
        boolean ok = true;
        int expectedStart = 0;
        List<Complexity.ComplexityGroup> groups = new ArrayList<>();

        while (m.find()) {
            if (m.start() != expectedStart) {
                throw new RuntimeException("Cannot parse filters");
            }

            groups.add(
                new Complexity.ComplexityGroup(
                    m.group("name"),
                    m.group("chars"),
                    Integer.parseInt(m.group("min"))
                )
            );

            expectedStart = m.end();
            ok = m.end() == m.regionEnd();
        }
        if (!ok) {
            throw new IllegalArgumentException("Cannot parse filters");
        }

        this.complexity = new Complexity(groups);;
        }
}
