CREATE INDEX IF NOT EXISTS idx_user_groups_user_id ON user_groups (user_id);
CREATE INDEX IF NOT EXISTS idx_group_attributes_group_id ON group_attributes (group_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_in_group_id ON user_groups (in_group_id);
CREATE INDEX IF NOT EXISTS idx_group_groups_group_id ON group_groups (group_id);
CREATE INDEX IF NOT EXISTS idx_user_password_history_user_id ON user_password_history (user_id);
CREATE INDEX IF NOT EXISTS idx_user_attributes_user_id ON user_attributes (user_id);
CREATE INDEX IF NOT EXISTS idx_group_groups_in_group_id ON group_groups (in_group_id);
CREATE INDEX IF NOT EXISTS idx_failed_logins_user_id ON failed_logins (user_id);
