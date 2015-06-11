-- Insure all SEQUENCES has the right number
SELECT setval('schema_version_seq', max(id)) FROM schema_version;
