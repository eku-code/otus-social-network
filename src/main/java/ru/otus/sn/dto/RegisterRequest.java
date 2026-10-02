package ru.otus.sn.dto;

public record RegisterRequest(
        String first_name,
        String second_name,
        String birthdate,
        String biography,
        String city,
        String gender,
        String password
) {
}
