-- PostgreSQL
INSERT INTO settings(uuid, name, value, description)
VALUES

--
-- internationalization
--

-- ('ad5bc526-fd58-4c35-9182-42913d45a9c2', 'WEEK_START_SUNDAY', TRUE, 'if true read user login_allowed as starting from Sunday else start from Monday'),

--
-- Account related
--
('bb90c43c-45cb-4af9-8c3e-4f6dee6ba60b', 'MAX_LOGIN_MINUTES', 60 * 24 * 7, 'session global maximum in minutes. -1 = no limit \n actual value subject to user validity and user allowed hours'),
('0ae5affd-15e5-4bb1-9910-f091b64b7197', 'PRESENT_WELCOME_MESSAGE', FALSE, 'present traditional unix welcome message'),
('df496823-6814-4720-a302-c723f629f847', 'MESSAGE_OF_THE_DAY', '', 'message for all users'),
('ecf6d62a-10f8-4fad-b401-75c9d0788955', 'MESSAGE_SEPARATOR', '\n', ''),

--
-- Brute force
--
('1a496c2f-02d7-4057-bfeb-23019d3941ae', 'MINIMUM_RESPONSE_SECONDS', 5, 'never respond to authentication requests in less then X seconds'),
('d9fce842-1906-40c0-b8d0-f01d2454623b', 'MAX_FAILURES_PER_MINUTE', 20, 'allow only X bad attempts in a minute. does not lock'),

--
-- Locking
--

--
('d03f5b67-5dca-4730-b1ac-01a6e8f31f25', 'MAX_FAILURES_SINCE_SUCCESS', 5, 'lock after X consecutive failures'),
('3b1e221c-21d2-45c9-95c5-2f71deec3b9f', 'MAX_FAILURES_PER_INTERVAL', 20, 'lock after X failures in interval. see INTERVAL_HOURS'),
('756f2a73-be20-4103-b82f-f41dae35f8bf', 'INTERVAL_HOURS', 24, 'determines the number of hours constituting an interval. see MAX_FAILURES_PER_INTERVAL'),
('78b5138a-d52b-464d-a2a7-5fed55bdf7b3', 'LOCK_MINUTES', 60, 'lock for X minutes'),

--
-- Encryption & Passwords
--

('8669297e-cfd7-45e3-80cd-a464c90694d7', 'PBE_ALGORITHM', 'PBKDF2WithHmacSHA1', 'Algorithm name. see http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html'),
('fa528912-4f3a-4c3a-8e93-561e92d785e8', 'PBE_ITERATIONS', 2000, 'Iterations'),
('8aa785db-bd8e-4ddd-9590-185b8a870f6f', 'PBE_KEY_SIZE', 256, 'Key Size'),

('dc3e2fb4-cbcc-4f5c-9b06-5db9ec534aa8','PASSWORD_EXPIRATION_DAYS', 180, 'upon expired password change, new password is valid to X days'),
('69d5cec2-bd1a-42e1-84f0-05627ee476b3','PASSWORD_EXPIRATION_NOTICE_DAYS', 0, 'show message X days before expiration'),
('e843bc2a-0878-4b6f-9be3-32e83169fb7c', 'PASSWORD_HISTORY_LIMIT', 3, 'number of old passwords to keep/check against in password change'),
--
('aaa93f69-7b75-44ee-b8a7-4d4736e73be1', 'ALLOW_EXPIRED_PASSWORD_CHANGE', FALSE, 'if true when a password expires the user can change it'),

--
-- Password complexity
--

-------------------------
-- Password Complexity --
-------------------------

('b55243d1-27b5-49bf-8436-67d5ada33975', 'PASSWORD_COMPLEXITY',
'UPPERCASE:chars=ABCDEFGHIJKLMNOPQRSTUVWXYZ::min=-1::LOWERCASE:chars=abcdefghijklmnopqrstuvwxyz::min=-1::NUMBERS:chars=0123456789::min=-1::',
'complexity groups definition. format:\n[name:chars=x::min=y::...]\nmin=-1 no limit. following chars should be escaped: \\t, \\n, \\f, \\'', \\" \\\\'),
('24e7de2f-a714-4f3e-8f64-13bc6ee7525b', 'MIN_LENGTH', 6, 'passwords are at least X characters long'),

--
-- Search queries
--

('8a077fb6-7271-4d79-a0b5-aa9a84384b69', 'MAX_PAGE_SIZE', 200, 'searches never return pages larger then X records'),

--
-- System related
--

('fba813af-4c30-448f-8df9-9334449c149e', 'SETTINGS_INTERVAL_MINUTES', -1, 'attempt to refresh settings for settings table at most every X minutes. -1 = never'),
('d89abf58-f0ca-4be8-8ec3-45a1f3645c7b', 'HOUSE_KEEPING_INTERVAL_HOURS', 24, 'perform house keeping tasks every X hours. -1 = never'),
('12fb6fee-797b-47e7-83e1-1f1fd8b8dd05', 'FAILED_LOGINS_OLD_DAYS', 7, 'during house keeping, delete failed logins older then X days. -1 = never');

