package ru.langsot.dating.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.langsot.dating.service.TelegramBotClient;
import tools.jackson.databind.JsonNode;

/** Прием обновлений от Telegram Bot API: на команду /start бот присылает кнопку запуска мини-приложения. */
@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramBotClient bot;

    public TelegramWebhookController(TelegramBotClient bot) {
        this.bot = bot;
    }

    @PostMapping("/api/telegram/webhook")
    public ResponseEntity<Void> onUpdate(@RequestBody JsonNode update) {
        JsonNode message = update.path("message");
        String text = message.path("text").asString("");
        if (text.startsWith("/start") && message.path("chat").has("id")) {
            bot.sendMessage(message.path("chat").path("id").asLong(),
                    "Привет! Здесь можно найти новых знакомых среди пользователей Telegram. Нажмите кнопку ниже.",
                    "Открыть знакомства");
            return ResponseEntity.ok().build();
        }
        log.debug("Webhook: обновление без команды /start пропущено");
        return ResponseEntity.ok().build();
    }
}
