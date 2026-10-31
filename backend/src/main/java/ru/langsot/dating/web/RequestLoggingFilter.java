package ru.langsot.dating.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Журнал запросов к API: метод, адрес, код ответа, время и пользователь. Тела запросов и токены не пишутся. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            write(request, response.getStatus(), (System.nanoTime() - started) / 1_000_000);
        }
    }

    private void write(HttpServletRequest request, int status, long millis) {
        String path = request.getRequestURI();
        Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR);
        String format = "{} {} -> {} за {} мс, пользователь {}";
        if (status >= 500) {
            log.error(format, request.getMethod(), path, status, millis, userId);
        } else if (status >= 400) {
            log.warn(format, request.getMethod(), path, status, millis, userId);
        } else if (path.startsWith("/api/photos/")) {
            // Фотографии запрашиваются десятками на экран и зашумили бы журнал
            log.debug(format, request.getMethod(), path, status, millis, userId);
        } else {
            log.info(format, request.getMethod(), path, status, millis, userId);
        }
    }
}
