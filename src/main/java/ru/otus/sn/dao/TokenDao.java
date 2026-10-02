package ru.otus.sn.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TokenDao {

    private final JdbcTemplate jdbc;

    public TokenDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String token, UUID userId) {
        jdbc.update("INSERT INTO tokens (token, user_id) VALUES (?, ?)", UUID.fromString(token), userId);
    }

    public Optional<UUID> findUserId(String token) {
        try {
            UUID tokenUuid = UUID.fromString(token);
            List<UUID> userIds = jdbc.queryForList(
                    "SELECT user_id FROM tokens WHERE token = ?", UUID.class, tokenUuid);
            return userIds.stream().findFirst();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
