package ru.otus.sn.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void shouldKeepStatusAndMessage() {
        ApiError error = new ApiError(400, "bad request");

        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getMessage()).isEqualTo("bad request");
    }
}
