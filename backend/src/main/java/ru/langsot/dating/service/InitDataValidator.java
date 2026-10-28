package ru.langsot.dating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.exception.UnauthorizedException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Проверка подлинности строки initData, которую Telegram передает мини-приложению.
 * Алгоритм: secret_key = HMAC_SHA256(key = "WebAppData", data = bot_token);
 * hash = hex(HMAC_SHA256(key = secret_key, data = data_check_string)).
 */
@Component
public class InitDataValidator {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secretKey;
    private final Duration maxAge;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public InitDataValidator(AppProperties props, Clock clock, ObjectMapper objectMapper) {
        this(props.telegram().botToken(), props.telegram().initDataMaxAge(), clock, objectMapper);
    }

    public InitDataValidator(String botToken, Duration maxAge, Clock clock, ObjectMapper objectMapper) {
        if (botToken == null || botToken.isBlank()) {
            this.secretKey = null;
        } else {
            this.secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        }
        this.maxAge = maxAge;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public TelegramInitData validate(String rawInitData) {
        if (secretKey == null) {
            throw new IllegalStateException("Не задан токен бота (app.telegram.bot-token)");
        }
        if (rawInitData == null || rawInitData.isBlank()) {
            throw new UnauthorizedException("Пустые данные initData");
        }
        Map<String, String> fields = parse(rawInitData);
        String hash = fields.remove("hash");
        if (hash == null) {
            throw new UnauthorizedException("В initData отсутствует подпись");
        }
        String expected = HexFormat.of().formatHex(hmac(secretKey, dataCheckString(fields).getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                hash.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new UnauthorizedException("Подпись initData неверна");
        }
        Instant authDate = parseAuthDate(fields.get("auth_date"));
        if (authDate.plus(maxAge).isBefore(clock.instant())) {
            throw new UnauthorizedException("Данные initData устарели");
        }
        return new TelegramInitData(parseUser(fields.get("user")), authDate, fields.get("query_id"));
    }

    /** Строка проверки данных: пары key=value без hash, отсортированные по ключу, через \n. */
    static String dataCheckString(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** Подписывает набор полей (используется в тестах и при отладке вне Telegram). */
    public static String sign(Map<String, String> fields, String botToken) {
        byte[] key = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac(key, dataCheckString(fields).getBytes(StandardCharsets.UTF_8)));
    }

    static Map<String, String> parse(String raw) {
        Map<String, String> result = new TreeMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new UnauthorizedException("Некорректный формат initData");
            }
            result.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return result;
    }

    private Instant parseAuthDate(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException e) {
            throw new UnauthorizedException("Некорректное поле auth_date");
        }
    }

    private TelegramUser parseUser(String json) {
        if (json == null) {
            throw new UnauthorizedException("В initData отсутствуют данные пользователя");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return new TelegramUser(node.get("id").asLong(), text(node, "username"),
                    text(node, "first_name"), text(node, "last_name"), text(node, "language_code"));
        } catch (Exception e) {
            throw new UnauthorizedException("Некорректные данные пользователя в initData");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record TelegramUser(long id, String username, String firstName, String lastName, String languageCode) {
    }

    public record TelegramInitData(TelegramUser user, Instant authDate, String queryId) {
    }
}
