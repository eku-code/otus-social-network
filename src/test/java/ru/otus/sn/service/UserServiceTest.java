package ru.otus.sn.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.otus.sn.dao.TokenDao;
import ru.otus.sn.dao.UserDao;
import ru.otus.sn.dto.RegisterRequest;
import ru.otus.sn.dto.UserResponse;
import ru.otus.sn.exception.ApiError;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserDao userDao;

    @Mock
    private TokenDao tokenDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest validRequest() {
        return new RegisterRequest(
                "Иван", "Иванов", "1990-05-15", "Хобби", "Москва", "male", "secret");
    }

    @Test
    void registerShouldInsertUserAndReturnId() {
        when(passwordEncoder.encode("secret")).thenReturn("hash");

        String id = userService.register(validRequest());

        assertThat(UUID.fromString(id)).isNotNull();
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userDao).insert(idCaptor.capture(), eq("Иван"), eq("Иванов"),
                eq(LocalDate.of(1990, 5, 15)), eq("Хобби"), eq("Москва"), eq("male"), eq("hash"));
        assertThat(idCaptor.getValue().toString()).isEqualTo(id);
        verify(tokenDao, never()).insert(anyString(), any());
    }

    @Test
    void registerShouldAllowBlankBirthdate() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        RegisterRequest req = new RegisterRequest("Иван", "Иванов", "  ", null, null, null, "secret");

        String id = userService.register(req);

        assertThat(UUID.fromString(id)).isNotNull();
        verify(userDao).insert(any(), eq("Иван"), eq("Иванов"),
                eq((LocalDate) null), eq((String) null), eq((String) null), eq((String) null), eq("hash"));
    }

    @Test
    void registerShouldFailWhenRequestIsNull() {
        assertThatThrownBy(() -> userService.register(null))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).getStatus())
                .isEqualTo(400);
    }

    @Test
    void registerShouldFailWhenFirstNameIsBlank() {
        RegisterRequest req = new RegisterRequest(" ", "Иванов", null, null, null, null, "secret");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(ApiError.class)
                .hasMessage("first_name, second_name and password are required");
    }

    @Test
    void registerShouldFailWhenSecondNameIsNull() {
        RegisterRequest req = new RegisterRequest("Иван", null, null, null, null, null, "secret");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(ApiError.class)
                .hasMessage("first_name, second_name and password are required");
    }

    @Test
    void registerShouldFailWhenPasswordIsBlank() {
        RegisterRequest req = new RegisterRequest("Иван", "Иванов", null, null, null, null, "");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(ApiError.class)
                .hasMessage("first_name, second_name and password are required");
    }

    @Test
    void registerShouldFailWhenGenderIsInvalid() {
        RegisterRequest req = new RegisterRequest("Иван", "Иванов", null, null, null, "other", "secret");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(ApiError.class)
                .hasMessage("gender must be male or female");
    }

    @Test
    void registerShouldFailWhenBirthdateIsMalformed() {
        RegisterRequest req = new RegisterRequest("Иван", "Иванов", "15.05.1990", null, null, null, "secret");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(ApiError.class)
                .hasMessage("birthdate must be YYYY-MM-DD");
    }

    @Test
    void loginShouldReturnTokenAndStoreIt() {
        UUID userId = UUID.randomUUID();
        when(userDao.findPasswordHash(userId)).thenReturn(Optional.of("hash"));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        String token = userService.login(userId.toString(), "secret");

        assertThat(UUID.fromString(token)).isNotNull();
        verify(tokenDao).insert(token, userId);
    }

    @Test
    void loginShouldFailWhenIdIsMissing() {
        assertThatThrownBy(() -> userService.login(null, "secret"))
                .isInstanceOf(ApiError.class)
                .hasMessage("id and password are required");
    }

    @Test
    void loginShouldFailWhenIdIsBlank() {
        assertThatThrownBy(() -> userService.login("  ", "secret"))
                .isInstanceOf(ApiError.class)
                .hasMessage("id and password are required");
    }

    @Test
    void loginShouldFailWhenPasswordIsMissing() {
        assertThatThrownBy(() -> userService.login("00000000-0000-0000-0000-000000000001", null))
                .isInstanceOf(ApiError.class)
                .hasMessage("id and password are required");
    }

    @Test
    void loginShouldFailWhenIdIsNotUuid() {
        assertThatThrownBy(() -> userService.login("not-a-uuid", "secret"))
                .isInstanceOf(ApiError.class)
                .hasMessage("invalid uuid");
    }

    @Test
    void loginShouldFailWhenUserIsUnknown() {
        when(userDao.findPasswordHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(UUID.randomUUID().toString(), "secret"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).getStatus())
                .isEqualTo(404);
    }

    @Test
    void loginShouldFailWhenPasswordDoesNotMatch() {
        UUID userId = UUID.randomUUID();
        when(userDao.findPasswordHash(userId)).thenReturn(Optional.of("hash"));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(userId.toString(), "wrong"))
                .isInstanceOf(ApiError.class)
                .hasMessage("user not found");
        verify(tokenDao, never()).insert(anyString(), any());
    }

    @Test
    void getByIdShouldReturnUser() {
        UUID userId = UUID.randomUUID();
        UserDao.UserResponseRow row = new UserDao.UserResponseRow(
                userId.toString(), "Иван", "Иванов", "1990-05-15", "Хобби", "Москва", "male");
        when(userDao.findById(userId)).thenReturn(Optional.of(row));

        UserResponse response = userService.getById(userId.toString());

        assertThat(response.user_id()).isEqualTo(userId.toString());
        assertThat(response.first_name()).isEqualTo("Иван");
        assertThat(response.second_name()).isEqualTo("Иванов");
        assertThat(response.birthdate()).isEqualTo("1990-05-15");
        assertThat(response.biography()).isEqualTo("Хобби");
        assertThat(response.city()).isEqualTo("Москва");
        assertThat(response.gender()).isEqualTo("male");
    }

    @Test
    void getByIdShouldFailWhenIdIsNotUuid() {
        assertThatThrownBy(() -> userService.getById("abc"))
                .isInstanceOf(ApiError.class)
                .hasMessage("invalid uuid");
    }

    @Test
    void getByIdShouldFailWhenUserIsUnknown() {
        when(userDao.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(UUID.randomUUID().toString()))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).getStatus())
                .isEqualTo(404);
    }
}
