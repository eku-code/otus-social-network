package ru.otus.sn.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.otus.sn.dao.TokenDao;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerTokenAuthFilterTest {

    @Mock
    private TokenDao tokenDao;

    private BearerTokenAuthFilter filter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @BeforeEach
    void setUp() {
        filter = new BearerTokenAuthFilter(tokenDao);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetAuthenticationForValidToken() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer " + UUID.randomUUID());
        when(tokenDao.findUserId(any())).thenReturn(Optional.of(userId));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void shouldNotSetAuthenticationWhenHeaderIsMissing() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotSetAuthenticationWhenHeaderIsNotBearer() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotSetAuthenticationWhenTokenIsBlank() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer    ");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotSetAuthenticationWhenTokenIsUnknown() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer " + UUID.randomUUID());
        when(tokenDao.findUserId(any())).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldTrimToken() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        request.addHeader("Authorization", "Bearer   " + token + "  ");
        when(tokenDao.findUserId(token)).thenReturn(Optional.of(userId));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(tokenDao).findUserId(token);
    }

    @Test
    void shouldAlwaysContinueChain() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
