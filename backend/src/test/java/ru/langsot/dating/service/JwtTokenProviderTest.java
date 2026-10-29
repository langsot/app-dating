package ru.langsot.dating.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.langsot.dating.exception.UnauthorizedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider — токены доступа")
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-32";
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private JwtTokenProvider at(Instant t) {
        return new JwtTokenProvider(SECRET, Duration.ofHours(12), Clock.fixed(t, ZoneOffset.UTC), new JsonMapper());
    }

    @Test
    @DisplayName("Т-07 выданный токен возвращает id пользователя")
    void roundTrip() {
        String token = at(NOW).createToken(42L);
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(at(NOW.plusSeconds(60)).parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Т-08 токен с подмененным id: отказ")
    void tampered() {
        String token = at(NOW).createToken(42L);
        String[] p = token.split("\\.");
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"1\",\"iat\":" + NOW.getEpochSecond() + ",\"exp\":" + (NOW.getEpochSecond() + 3600) + "}").getBytes());
        assertThatThrownBy(() -> at(NOW).parseUserId(p[0] + "." + forged + "." + p[2]))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("Подпись");
    }

    @Test
    @DisplayName("Т-09 истекший токен: отказ")
    void expired() {
        String token = at(NOW).createToken(42L);
        assertThatThrownBy(() -> at(NOW.plus(Duration.ofHours(13))).parseUserId(token))
                .isInstanceOf(UnauthorizedException.class).hasMessageContaining("истек");
    }

    @Test
    @DisplayName("короткий секрет запрещен конфигурацией")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", Duration.ofHours(1), Clock.systemUTC(), new JsonMapper()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
