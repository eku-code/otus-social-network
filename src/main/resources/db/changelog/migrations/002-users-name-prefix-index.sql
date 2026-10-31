-- Индекс для префиксного поиска анкет по фамилии и имени.
-- text_pattern_ops нужен, чтобы B-tree работал с LIKE 'префикс%'
-- при не-C локали (ru_RU.UTF-8).
--changeset otus:002-users-name-prefix-index
CREATE INDEX IF NOT EXISTS idx_users_second_first_name_pattern
    ON users (second_name text_pattern_ops, first_name text_pattern_ops);
