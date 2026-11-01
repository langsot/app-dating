package ru.langsot.dating.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };
    private final RateLimitFilter filter = new RateLimitFilter(clock, JsonMapper.builder().build(), 2);

    private int call(String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("CF-Connecting-IP", address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void т48_сверх_лимита_429_у_других_адресов_и_в_новую_минуту_запросы_проходят() throws Exception {
        assertEquals(200, call("/api/matches", "1.1.1.1"));
        assertEquals(200, call("/api/feed", "1.1.1.1"));
        assertEquals(429, call("/api/matches", "1.1.1.1"));
        assertEquals(200, call("/api/matches", "2.2.2.2"));
        now = now.plusSeconds(60);
        assertEquals(200, call("/api/matches", "1.1.1.1"));
    }

    @Test
    void т49_фото_и_webhook_не_ограничиваются() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, call("/api/photos/a.jpg", "1.1.1.1"));
            assertEquals(200, call("/api/telegram/webhook", "1.1.1.1"));
        }
    }
}
