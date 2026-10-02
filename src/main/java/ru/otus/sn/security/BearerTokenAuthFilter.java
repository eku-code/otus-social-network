package ru.otus.sn.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.otus.sn.dao.TokenDao;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private final TokenDao tokenDao;

    public BearerTokenAuthFilter(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        getBearerToken(request)
                .flatMap(tokenDao::findUserId)
                .ifPresent(userId -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, AuthorityUtils.NO_AUTHORITIES);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        filterChain.doFilter(request, response);
    }

    private Optional<String> getBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }
}
