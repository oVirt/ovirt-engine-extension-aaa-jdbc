CREATE OR REPLACE FUNCTION convert_password(old_password VARCHAR) RETURNS TEXT STABLE AS
$BODY$
DECLARE
  _items TEXT[];
BEGIN
  _items := regexp_split_to_array(old_password, E'\\|+');
  RETURN
    encode(
      convert_to(
        '{"artifact":"EnvelopePBE","salt":"' || _items[3] ||
        '","secret":"' || _items[5] ||
        '","version":"' || _items[1] ||
        '","iterations":"' || _items[4] ||
        '","algorithm":"' || _items[2] ||
        '"}',
        current_setting('server_encoding')
      ),
      'base64'
    );
END;
$BODY$
LANGUAGE PLPGSQL;

UPDATE users SET
  password = convert_password(password)
  WHERE
    password IS NOT NULL AND
    substring(password from 1 for 2) = '1|';

UPDATE user_password_history SET
  password = convert_password(password)
  WHERE
    password IS NOT NULL AND
    substring(password from 1 for 2) = '1|';

