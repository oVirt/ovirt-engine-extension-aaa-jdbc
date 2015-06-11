INSERT INTO schema_version(version,script,checksum,installed_by,ended_at,state,current)
  values ('01000000','upgrade/01_00_0000_set_version.sql','0','engine',now(),'INSTALLED',true);

