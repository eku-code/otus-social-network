package ru.otus.sn.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserDao {

    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String firstName, String secondName, LocalDate birthdate,
                       String biography, String city, String gender, String passwordHash) {
        jdbc.update(
                "INSERT INTO users (id, first_name, second_name, birthdate, biography, city, gender, password_hash) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setString(2, firstName);
                    ps.setString(3, secondName);
                    if (birthdate != null) {
                        ps.setDate(4, Date.valueOf(birthdate));
                    } else {
                        ps.setNull(4, Types.DATE);
                    }
                    ps.setString(5, biography);
                    ps.setString(6, city);
                    ps.setString(7, gender);
                    ps.setString(8, passwordHash);
                });
    }

    public Optional<String> findPasswordHash(UUID id) {
        List<String> hashes = jdbc.queryForList(
                "SELECT password_hash FROM users WHERE id = ?", String.class, id);
        return hashes.stream().findFirst();
    }

    public Optional<UserResponseRow> findById(UUID id) {
        List<UserResponseRow> rows = jdbc.query(
                "SELECT id, first_name, second_name, birthdate, biography, city, gender FROM users WHERE id = ?",
                (rs, rowNum) -> new UserResponseRow(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("first_name"),
                        rs.getString("second_name"),
                        rs.getObject("birthdate") == null ? null : rs.getDate("birthdate").toString(),
                        rs.getString("biography"),
                        rs.getString("city"),
                        rs.getString("gender")),
                id);
        return rows.stream().findFirst();
    }

    public record UserResponseRow(
            String id,
            String firstName,
            String secondName,
            String birthdate,
            String biography,
            String city,
            String gender) {
    }
}
