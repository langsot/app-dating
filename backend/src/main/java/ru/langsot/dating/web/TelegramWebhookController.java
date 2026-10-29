package ru.langsot.dating.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.langsot.dating.service.TelegramBotClient;

/** Прием обновлений от Telegram Bot API: на команду /start бот присылает кнопку запуска мини-приложения. */
@RestController
public class TelegramWebhookController {

    private final TelegramBotClient bot;

    public TelegramWebhookController(TelegramBotClient bot) {
        this.bot = bot;
    }

    @PostMapping("/api/telegram/webhook")
    public ResponseEntity<Void> onUpdate(@RequestBody JsonNode update) {
        JsonNode message = update.path("message");
        String text = message.path("text").asText("");
        if (text.startsWith("/start") && message.path("chat").has("id")) {
            bot.sendMessage(message.path("chat").path("id").asLong(),
                    "Привет! Здесь можно найти новых знакомых среди пользователей Telegram. Нажмите кнопку ниже.",
                    "Открыть знакомства");
        }
        return ResponseEntity.ok().build();
    }
}
