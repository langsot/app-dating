package ru.langsot.dating.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.exception.UnauthorizedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/** Создание и проверка токенов доступа JWT (алгоритм HS256). */
@Component
public class JwtTokenProvider {

    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    @Autowired
    public JwtTokenProvider(AppProperties props, Clock clock, JsonMapper jsonMapper) {
        this(props.jwt().secret(), props.jwt().ttl(), clock, jsonMapper);
    }

    public JwtTokenProvider(String secret, Duration ttl, Clock clock, JsonMapper jsonMapper) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Секрет JWT должен содержать не менее 32 символов");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    public String createToken(Long userId) {
        long now = clock.instant().getEpochSecond();
        String payload = b64(("{\"sub\":\"" + userId + "\",\"iat\":" + now + ",\"exp\":" + (now + ttl.toSeconds()) + "}")
                .getBytes(StandardCharsets.UTF_8));
        String unsigned = HEADER + "." + payload;
        return unsigned + "." + b64(sign(unsigned));
    }

    /** Возвращает идентификатор пользователя из действительного токена. */
    public Long parseUserId(String token) {
        if (token == null) {
            throw new UnauthorizedException("Отсутствует токен");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !parts[0].equals(HEADER)) {
            throw new UnauthorizedException("Некорректный токен");
        }
        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Некорректный токен");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new UnauthorizedException("Подпись токена неверна");
        }
        try {
            JsonNode payload = jsonMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (payload.get("exp").asLong() < clock.instant().getEpochSecond()) {
                throw new UnauthorizedException("Срок действия токена истек");
            }
            return Long.parseLong(payload.get("sub").asString());
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Некорректный токен");
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
