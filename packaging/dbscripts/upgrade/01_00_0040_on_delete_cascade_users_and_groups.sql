SELECT fn_db_drop_constraint('user_groups', 'user_groups_in_group_id_fkey');
SELECT fn_db_create_constraint('user_groups', 'user_groups_in_group_id_fkey', 'FOREIGN KEY (in_group_id) REFERENCES groups (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE CASCADE');
SELECT fn_db_drop_constraint('group_groups', 'group_groups_in_group_id_fkey');
SELECT fn_db_create_constraint('group_groups', 'group_groups_in_group_id_fkey', 'FOREIGN KEY (in_group_id) REFERENCES groups (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE CASCADE');
