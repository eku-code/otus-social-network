package ru.otus.sn.dto;

public record UserResponse(
        String user_id,
        String first_name,
        String second_name,
        String birthdate,
        String biography,
        String city,
        String gender
) {
}
