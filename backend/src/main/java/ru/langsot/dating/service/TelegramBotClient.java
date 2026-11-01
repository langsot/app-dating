package ru.langsot.dating.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.langsot.dating.config.AppProperties;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Клиент Telegram Bot API (граничный класс для внешней системы). */
@Component
public class TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final AppProperties.Telegram cfg;
    private final JsonMapper jsonMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TelegramBotClient(AppProperties props, JsonMapper jsonMapper) {
        this.cfg = props.telegram();
        this.jsonMapper = jsonMapper;
    }

    public boolean isEnabled() {
        return cfg.botToken() != null && !cfg.botToken().isBlank();
    }

    /** Отправляет сообщение с кнопкой, открывающей мини-приложение. Возвращает true при успехе. */
    public boolean sendMessage(long chatId, String text, String buttonText) {
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard",
                        List.of(List.of(Map.of("text", buttonText, "web_app", Map.of("url", cfg.webAppUrl()))))));
        if (!isEnabled()) {
            log.info("Telegram отключен (нет токена), сообщение для {} не отправлено: {}", chatId, text);
            return false;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.apiBaseUrl() + "/bot" + cfg.botToken() + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                .build();
        // Повтор только при сбое связи или сервера: ответ 4xx при повторе не изменится
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return true;
                }
                log.warn("Telegram Bot API вернул {} (попытка {} из {}): {}", resp.statusCode(), attempt, MAX_ATTEMPTS, resp.body());
                if (resp.statusCode() < 500) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("Не удалось отправить сообщение в Telegram (попытка {} из {}): {}", attempt, MAX_ATTEMPTS, e.toString());
            }
            if (attempt < MAX_ATTEMPTS && !pause()) {
                return false;
            }
        }
        return false;
    }

    private boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
