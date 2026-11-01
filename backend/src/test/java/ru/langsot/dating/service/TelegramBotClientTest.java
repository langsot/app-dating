package ru.langsot.dating.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.langsot.dating.config.AppProperties;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Клиент Telegram Bot API")
class TelegramBotClientTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> paths = new ArrayList<>();

    private TelegramBotClient clientAnswering(int... statuses) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            int status = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static TelegramBotClient client(String baseUrl) {
        AppProperties props = new AppProperties(new AppProperties.Telegram("1:token", "bot", "https://app.example",
                Duration.ofHours(24), baseUrl), null, null, null);
        return new TelegramBotClient(props, JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Т-46 запрос идет на настроенный адрес, сбой сервера повторяется")
    void retriesServerErrorOnConfiguredUrl() throws IOException {
        TelegramBotClient bot = clientAnswering(502, 200);
        assertThat(bot.sendMessage(7L, "текст", "кнопка")).isTrue();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(paths.get(0)).isEqualTo("/bot1:token/sendMessage");
    }

    @Test
    @DisplayName("Т-47 ошибка 4xx не повторяется")
    void doesNotRetryClientError() throws IOException {
        TelegramBotClient bot = clientAnswering(403);
        assertThat(bot.sendMessage(7L, "текст", "кнопка")).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }
}
