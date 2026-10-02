package ru.otus.sn.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.otus.sn.IntegrationTestBase;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDaoIT extends IntegrationTestBase {

    @Autowired
    private TokenDao tokenDao;
    @Autowired
    private UserDao userDao;


    @Test
    void insertAndFindUserIdShouldRoundTrip() {
        UUID userId = createUser();
        String token = UUID.randomUUID().toString();

        tokenDao.insert(token, userId);

        Optional<UUID> found = tokenDao.findUserId(token);
        assertThat(found).contains(userId);
    }

    @Test
    void findUserIdShouldReturnEmptyForUnknownToken() {
        assertThat(tokenDao.findUserId(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void findUserIdShouldReturnEmptyForMalformedToken() {
        assertThat(tokenDao.findUserId("not-a-uuid")).isEmpty();
    }

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        userDao.insert(userId, "Иван", "Иванов", null, null, null, null, "hash");
        return userId;
    }
}
