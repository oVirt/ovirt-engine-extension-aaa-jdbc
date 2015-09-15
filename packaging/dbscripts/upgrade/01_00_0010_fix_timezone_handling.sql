SELECT fn_db_change_column_type('users', 'password_valid_to', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('users', 'last_successful_login', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('users', 'last_unsuccessful_login', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('users', 'valid_from', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('users', 'valid_to', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('user_password_history', 'changed', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');
SELECT fn_db_change_column_type('failed_logins', 'minute_start', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE');

