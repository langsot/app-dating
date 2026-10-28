package ru.langsot.dating;

import ru.langsot.dating.service.InitDataValidator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Вспомогательные данные для тестов: формирование подписанной строки initData так, как это делает Telegram. */
public final class TestData {

    public static final String BOT_TOKEN = "123456789:TEST-bot-token-for-unit-tests";

    private TestData() {
    }

    public static Map<String, String> fields(long tgId, String firstName, String username, Instant authDate) {
        Map<String, String> f = new TreeMap<>();
        f.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        f.put("query_id", "AAH" + tgId);
        f.put("user", "{\"id\":" + tgId + ",\"first_name\":\"" + firstName + "\",\"username\":\"" + username
                + "\",\"language_code\":\"ru\"}");
        return f;
    }

    /** Строка initData с корректной подписью (параметры кодируются как в URL). */
    public static String initData(long tgId, String firstName, String username, Instant authDate, String botToken) {
        Map<String, String> f = fields(tgId, firstName, username, authDate);
        String hash = InitDataValidator.sign(f, botToken);
        return encode(f) + "&hash=" + hash;
    }

    public static String initData(long tgId, String firstName) {
        return initData(tgId, firstName, "user" + tgId, Instant.now(), BOT_TOKEN);
    }

    public static String encode(Map<String, String> f) {
        return f.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Минимальный корректный PNG (1x1). */
    public static byte[] png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    }
}
