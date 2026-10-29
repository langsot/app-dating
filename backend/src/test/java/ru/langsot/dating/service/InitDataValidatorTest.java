package ru.langsot.dating.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.langsot.dating.TestData;
import ru.langsot.dating.exception.UnauthorizedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InitDataValidator — проверка подписи данных Telegram")
class InitDataValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private final InitDataValidator validator = new InitDataValidator(TestData.BOT_TOKEN, Duration.ofHours(24),
            Clock.fixed(NOW, ZoneOffset.UTC), new JsonMapper());

    @Test
    @DisplayName("Т-01 корректная подпись: данные пользователя извлечены")
    void validSignature() {
        String raw = TestData.initData(1001, "Анна", "anna", NOW.minusSeconds(60), TestData.BOT_TOKEN);
        InitDataValidator.TelegramInitData data = validator.validate(raw);
        assertThat(data.user().id()).isEqualTo(1001);
        assertThat(data.user().firstName()).isEqualTo("Анна");
        assertThat(data.user().username()).isEqualTo("anna");
    }

    @Test
    @DisplayName("Т-02 изменен идентификатор пользователя после подписи: отказ")
    void tamperedUser() {
        String raw = TestData.initData(1001, "Анна", "anna", NOW, TestData.BOT_TOKEN).replace("1001", "1002");
        assertThatThrownBy(() -> validator.validate(raw)).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Подпись initData неверна");
    }

    @Test
    @DisplayName("Т-03 подпись чужим токеном бота: отказ")
    void foreignBotToken() {
        String raw = TestData.initData(1001, "Анна", "anna", NOW, "999:OTHER");
        assertThatThrownBy(() -> validator.validate(raw)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Т-04 данные старше 24 часов: отказ")
    void expired() {
        String raw = TestData.initData(1001, "Анна", "anna", NOW.minus(Duration.ofHours(25)), TestData.BOT_TOKEN);
        assertThatThrownBy(() -> validator.validate(raw)).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("устарели");
    }

    @Test
    @DisplayName("Т-05 отсутствует hash: отказ")
    void missingHash() {
        String raw = TestData.encode(TestData.fields(1001, "Анна", "anna", NOW));
        assertThatThrownBy(() -> validator.validate(raw)).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("отсутствует подпись");
    }

    @Test
    @DisplayName("Т-06 пустая и некорректная строка: отказ")
    void malformed() {
        assertThatThrownBy(() -> validator.validate("")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> validator.validate("abc")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("строка проверки данных: пары отсортированы и разделены переводом строки")
    void dataCheckString() {
        var f = new java.util.HashMap<String, String>();
        f.put("user", "u");
        f.put("auth_date", "1");
        f.put("query_id", "q");
        assertThat(InitDataValidator.dataCheckString(f)).isEqualTo("auth_date=1\nquery_id=q\nuser=u");
    }
}
