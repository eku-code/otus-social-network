package ru.otus.sn.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.otus.sn.dao.TokenDao;
import ru.otus.sn.dao.UserDao;
import ru.otus.sn.dto.RegisterRequest;
import ru.otus.sn.dto.UserResponse;
import ru.otus.sn.exception.ApiError;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserDao userDao;
    private final TokenDao tokenDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, TokenDao tokenDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.passwordEncoder = passwordEncoder;
    }

    public String register(RegisterRequest req) {
        validate(req);
        LocalDate birthdate = parseBirthdate(req.birthdate());
        UUID id = UUID.randomUUID();
        userDao.insert(id, req.first_name(), req.second_name(), birthdate,
                req.biography(), req.city(), req.gender(), passwordEncoder.encode(req.password()));
        return id.toString();
    }

    public String login(String id, String password) {
        if (id == null || id.isBlank() || password == null || password.isBlank()) {
            throw new ApiError(400, "id and password are required");
        }
        UUID userId = parseUuid(id);
        String hash = userDao.findPasswordHash(userId)
                .orElseThrow(() -> new ApiError(404, "user not found"));
        if (!passwordEncoder.matches(password, hash)) {
            throw new ApiError(404, "user not found");
        }
        String token = UUID.randomUUID().toString();
        tokenDao.insert(token, userId);
        return token;
    }

    public UserResponse getById(String id) {
        UUID userId = parseUuid(id);
        return userDao.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiError(404, "user not found"));
    }

    public List<UserResponse> search(String firstName, String secondName) {
        if (isBlank(firstName) || isBlank(secondName)) {
            throw new ApiError(400, "first_name and last_name are required");
        }
        return userDao.searchByPrefix(firstName, secondName).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validate(RegisterRequest req) {
        if (req == null
                || isBlank(req.first_name())
                || isBlank(req.second_name())
                || isBlank(req.password())) {
            throw new ApiError(400, "first_name, second_name and password are required");
        }
        if (!isBlank(req.gender()) && !"male".equals(req.gender()) && !"female".equals(req.gender())) {
            throw new ApiError(400, "gender must be male or female");
        }
    }

    private LocalDate parseBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(birthdate);
        } catch (DateTimeParseException e) {
            throw new ApiError(400, "birthdate must be YYYY-MM-DD");
        }
    }

    private UserResponse toResponse(UserDao.UserResponseRow row) {
        return new UserResponse(row.id(), row.firstName(), row.secondName(),
                row.birthdate(), row.biography(), row.city(), row.gender());
    }

    private UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new ApiError(400, "invalid uuid");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
