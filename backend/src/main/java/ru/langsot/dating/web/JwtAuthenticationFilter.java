package ru.langsot.dating.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.langsot.dating.dto.Dtos.ErrorDto;
import ru.langsot.dating.exception.UnauthorizedException;
import ru.langsot.dating.service.JwtTokenProvider;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Проверка токена доступа для всех адресов /api/**, кроме публичных. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTR = "dating.userId";
    private static final List<String> PUBLIC_PREFIXES =
            List.of("/api/auth/", "/api/telegram/webhook", "/api/photos/", "/api/interests");

    private final JwtTokenProvider tokens;
    private final JsonMapper jsonMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokens, JsonMapper jsonMapper) {
        this.tokens = tokens;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header == null || !header.startsWith("Bearer ")) {
                throw new UnauthorizedException("Требуется авторизация");
            }
            request.setAttribute(USER_ID_ATTR, tokens.parseUserId(header.substring(7)));
        } catch (UnauthorizedException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getOutputStream(), new ErrorDto(401, "Unauthorized", e.getMessage(), List.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
