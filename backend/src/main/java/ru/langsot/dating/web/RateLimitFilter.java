package ru.langsot.dating.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.langsot.dating.dto.Dtos.ErrorDto;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничение частоты запросов к API с одного адреса за минуту. Фото и webhook Telegram не считаются.
 * Порт приложения наружу не опубликован, поэтому заголовку Cloudflare с адресом клиента можно доверять.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final int limit;
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private volatile long window;

    public RateLimitFilter(Clock clock, JsonMapper jsonMapper, @Value("${app.rate-limit-per-minute:300}") int limit) {
        this.clock = clock;
        this.jsonMapper = jsonMapper;
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/photos/") || path.startsWith("/api/telegram/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long current = clock.millis() / WINDOW_MILLIS;
        if (current != window) {
            window = current;
            counts.clear();
        }
        String address = request.getHeader("CF-Connecting-IP");
        int count = counts.merge(address != null ? address : request.getRemoteAddr(), 1, Integer::sum);
        if (count > limit) {
            // Адрес клиента в журнал не пишется
            if (count == limit + 1) log.warn("Превышен лимит запросов: {} в минуту", limit);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getOutputStream(),
                    new ErrorDto(429, "Too Many Requests", "Слишком много запросов. Повторите попытку через минуту.", List.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
