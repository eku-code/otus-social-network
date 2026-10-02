package ru.otus.sn.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.sn.dto.LoginRequest;
import ru.otus.sn.dto.TokenResponse;
import ru.otus.sn.service.UserService;

@RestController
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest req) {
        String token = userService.login(req.id(), req.password());
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
