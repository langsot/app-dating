package ru.langsot.dating.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Настройки приложения (раздел app в application.yml). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Telegram telegram, Jwt jwt, Storage storage, Feed feed) {

    public record Telegram(String botToken, String botUsername, String webAppUrl,
                           Duration initDataMaxAge, String apiBaseUrl) {
    }

    public record Jwt(String secret, Duration ttl) {
    }

    public record Storage(String dir) {
    }

    public record Feed(int pageSize) {
    }
}
