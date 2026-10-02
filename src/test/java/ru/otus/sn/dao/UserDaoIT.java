package ru.otus.sn.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.otus.sn.IntegrationTestBase;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDaoIT extends IntegrationTestBase {

    @Autowired
    private UserDao userDao;

    @Test
    void insertAndFindByIdShouldRoundTrip() {
        UUID id = UUID.randomUUID();

        userDao.insert(id, "Иван", "Иванов", LocalDate.of(1990, 5, 15),
                "Хобби", "Москва", "male", "hash");

        Optional<UserDao.UserResponseRow> found = userDao.findById(id);
        assertThat(found).isPresent();
        UserDao.UserResponseRow row = found.get();
        assertThat(row.id()).isEqualTo(id.toString());
        assertThat(row.firstName()).isEqualTo("Иван");
        assertThat(row.secondName()).isEqualTo("Иванов");
        assertThat(row.birthdate()).isEqualTo("1990-05-15");
        assertThat(row.biography()).isEqualTo("Хобби");
        assertThat(row.city()).isEqualTo("Москва");
        assertThat(row.gender()).isEqualTo("male");
    }

    @Test
    void insertShouldAllowNullBirthdateAndOptionals() {
        UUID id = UUID.randomUUID();

        userDao.insert(id, "Анна", "Петрова", null, null, null, "female", "hash2");

        Optional<UserDao.UserResponseRow> found = userDao.findById(id);
        assertThat(found).isPresent();
        UserDao.UserResponseRow row = found.get();
        assertThat(row.birthdate()).isNull();
        assertThat(row.biography()).isNull();
        assertThat(row.city()).isNull();
        assertThat(row.gender()).isEqualTo("female");
    }

    @Test
    void findByIdShouldReturnEmptyForUnknownId() {
        assertThat(userDao.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findPasswordHashShouldReturnHashForExistingUser() {
        UUID id = UUID.randomUUID();
        userDao.insert(id, "Иван", "Иванов", null, null, null, null, "secret-hash");

        assertThat(userDao.findPasswordHash(id)).contains("secret-hash");
    }

    @Test
    void findPasswordHashShouldReturnEmptyForUnknownUser() {
        assertThat(userDao.findPasswordHash(UUID.randomUUID())).isEmpty();
    }
}
