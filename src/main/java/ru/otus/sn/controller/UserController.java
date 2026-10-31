package ru.otus.sn.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.sn.dto.RegisterRequest;
import ru.otus.sn.dto.UserIdResponse;
import ru.otus.sn.dto.UserResponse;
import ru.otus.sn.service.UserService;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/user/register")
    public ResponseEntity<UserIdResponse> register(@RequestBody RegisterRequest req) {
        String userId = userService.register(req);
        return ResponseEntity.ok(new UserIdResponse(userId));
    }

    @GetMapping("/user/get/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/user/search")
    public ResponseEntity<List<UserResponse>> search(
            @RequestParam("first_name") String firstName,
            @RequestParam("last_name") String lastName) {
        return ResponseEntity.ok(userService.search(firstName, lastName));
    }
}
