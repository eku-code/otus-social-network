package ru.otus.sn.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.otus.sn.dto.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnApiErrorStatusAndMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleApiError(new ApiError(404, "user not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("user not found");
    }

    @Test
    void shouldHandleMessageNotReadable() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new HttpMessageNotReadableException("bad json"));

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("invalid request data");
    }

    @Test
    void shouldHandleTypeMismatch() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new MethodArgumentTypeMismatchException(null, String.class, "id", null, new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("invalid request data");
    }

    @Test
    void shouldHandleUnexpectedException() {
        ResponseEntity<ErrorResponse> response = handler.handleInternal(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("internal error");
    }
}
