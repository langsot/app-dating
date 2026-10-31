package ru.langsot.dating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.langsot.dating.domain.LikeType;
import ru.langsot.dating.service.MatchingService;
import ru.langsot.dating.service.TelegramBotClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты REST API на реальной СУБД PostgreSQL (база dating_test).
 * Внешняя система Telegram Bot API заменена тестовым двойником (mock).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/dating_test}",
        "spring.datasource.username=dating", "spring.datasource.password=${TEST_DB_PASSWORD:dating}",
        "app.telegram.bot-token=" + TestData.BOT_TOKEN,
        "app.jwt.secret=test-secret-test-secret-test-secret-32b",
        "app.storage.dir=${java.io.tmpdir}/dating-test-photos",
        "logging.level.root=WARN"})
@AutoConfigureMockMvc
@DisplayName("REST API — интеграционные тесты")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;
    @Autowired
    MatchingService matchingService;
    @MockitoBean
    TelegramBotClient bot;

    record Session(String token, long userId, long tgId) {
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE likes, matches, photos, profile_interests, profiles, users RESTART IDENTITY CASCADE");
        when(bot.sendMessage(anyLong(), anyString(), anyString())).thenReturn(true);
    }

    // ------------------------------------------------------------------ вспомогательные методы

    Session login(long tgId, String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("initData", TestData.initData(tgId, name)))))
                .andExpect(status().isOk()).andReturn();
        JsonNode n = json.readTree(r.getResponse().getContentAsString());
        return new Session(n.get("token").asString(), n.get("userId").asLong(), tgId);
    }

    String profileJson(String name, int age, String gender, String pref, int min, int max, String... interests)
            throws Exception {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("displayName", name);
        m.put("birthDate", LocalDate.now().minusYears(age).minusDays(10).toString());
        m.put("gender", gender);
        m.put("bio", "Тестовая анкета");
        m.put("city", "Томск");
        m.put("preferredGender", pref);
        m.put("minAge", min);
        m.put("maxAge", max);
        m.put("interests", List.of(interests));
        return json.writeValueAsString(m);
    }

    void saveProfile(Session s, String body) throws Exception {
        mvc.perform(put("/api/profile/me").header("Authorization", "Bearer " + s.token())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    JsonNode uploadPhoto(Session s) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/profile/me/photos")
                        .file(new MockMultipartFile("file", "p.png", "image/png", TestData.png()))
                        .header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    Session person(long tgId, String name, int age, String gender, String pref, int min, int max) throws Exception {
        Session s = login(tgId, name);
        saveProfile(s, profileJson(name, age, gender, pref, min, max));
        uploadPhoto(s);
        return s;
    }

    List<Long> feedIds(Session s) throws Exception {
        MvcResult r = mvc.perform(get("/api/feed").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn();
        List<Long> ids = new ArrayList<>();
        json.readTree(r.getResponse().getContentAsString()).forEach(n -> ids.add(n.get("userId").asLong()));
        return ids;
    }

    JsonNode rate(Session s, long target, String type) throws Exception {
        MvcResult r = mvc.perform(post("/api/feed/" + target + "/rate").header("Authorization", "Bearer " + s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    JsonNode matches(Session s) throws Exception {
        return json.readTree(mvc.perform(get("/api/matches").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ авторизация

    @Test
    @DisplayName("Т-20 первый вход создает пользователя, повторный — находит его")
    void loginCreatesAndFinds() throws Exception {
        mvc.perform(post("/api/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initData\":\"" + TestData.initData(500, "Анна").replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.profileComplete").value(false));
        Session again = login(500, "Анна");
        assertThat(again.userId()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Т-21 поддельные initData: 401 и сообщение об ошибке")
    void forgedInitData() throws Exception {
        String forged = TestData.initData(500, "Анна").replace("500", "501");
        mvc.perform(post("/api/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("initData", forged))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Подпись initData неверна"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Т-22 запрос без токена или с поддельным токеном: 401")
    void protectedEndpoints() throws Exception {
        mvc.perform(get("/api/profile/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется авторизация"));
        mvc.perform(get("/api/feed").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ анкета

    @Test
    @DisplayName("Т-23 сохранение анкеты и проверка входных данных")
    void profileValidation() throws Exception {
        Session s = login(600, "Иван");
        saveProfile(s, profileJson("Иван", 25, "MALE", "FEMALE", 20, 30, "кино", "IT"));
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(25))
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.complete").value(false));

        String[][] bad = {
                {profileJson("Иван", 16, "MALE", "FEMALE", 20, 30), "18"},
                {profileJson("Иван", 25, "MALE", "FEMALE", 35, 30), "Минимальный возраст"},
                {profileJson("Иван", 25, "MALE", "FEMALE", 20, 30, "вязание_крючком"), "Неизвестные интересы"},
                {profileJson(" ", 25, "MALE", "FEMALE", 20, 30), "Проверьте заполнение полей"},
        };
        for (String[] b : bad) {
            mvc.perform(put("/api/profile/me").header("Authorization", "Bearer " + s.token())
                            .contentType(MediaType.APPLICATION_JSON).content(b[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString(b[1])));
        }
    }

    @Test
    @DisplayName("Т-24 загрузка фотографий: выдача файла, лимит 6 шт., отказ для не-изображений")
    void photos() throws Exception {
        Session s = login(700, "Мария");
        saveProfile(s, profileJson("Мария", 24, "FEMALE", "MALE", 18, 40));
        String url = uploadPhoto(s).get("url").asString();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        for (int i = 0; i < 5; i++) {
            uploadPhoto(s);
        }
        mvc.perform(multipart("/api/profile/me/photos")
                        .file(new MockMultipartFile("file", "7.png", "image/png", TestData.png()))
                        .header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Можно загрузить не более 6 фотографий"));
        mvc.perform(multipart("/api/profile/me/photos")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes()))
                        .header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ лента

    @Test
    @DisplayName("Т-25 лента недоступна без фотографии в анкете: 409")
    void feedNeedsCompleteProfile() throws Exception {
        Session s = login(800, "Олег");
        saveProfile(s, profileJson("Олег", 30, "MALE", "ANY", 18, 45));
        mvc.perform(get("/api/feed").header("Authorization", "Bearer " + s.token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Т-26 лента учитывает взаимные параметры поиска и скрытые анкеты")
    void feedFilters() throws Exception {
        Session viewer = person(1, "Иван", 25, "MALE", "FEMALE", 20, 30);
        Session ok = person(2, "Анна", 24, "FEMALE", "MALE", 20, 30);
        Session tooOld = person(3, "Вера", 35, "FEMALE", "MALE", 20, 40);
        Session male = person(4, "Петр", 24, "MALE", "ANY", 20, 30);
        Session wantsFemale = person(5, "Ольга", 24, "FEMALE", "FEMALE", 20, 30);
        Session viewerTooOld = person(6, "Дарья", 24, "FEMALE", "ANY", 18, 22);
        Session hidden = person(7, "Ирина", 24, "FEMALE", "ANY", 18, 45);
        mvc.perform(patch("/api/profile/me/visibility").header("Authorization", "Bearer " + hidden.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andExpect(status().isNoContent());
        Session noPhoto = login(8, "Нина");
        saveProfile(noPhoto, profileJson("Нина", 24, "FEMALE", "ANY", 18, 45));

        assertThat(feedIds(viewer)).containsExactly(ok.userId());
        rate(viewer, ok.userId(), "DISLIKE");
        assertThat(feedIds(viewer)).isEmpty();
        assertThat(List.of(tooOld, male, wantsFemale, viewerTooOld)).isNotEmpty();
    }

    // ------------------------------------------------------------------ оценки и мэтчи

    @Test
    @DisplayName("Т-27 встречные лайки создают мэтч и уведомление обоим")
    void mutualLikeCreatesMatch() throws Exception {
        Session a = person(11, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(12, "Анна", 24, "FEMALE", "MALE", 18, 45);
        assertThat(rate(a, b.userId(), "LIKE").get("matched").asBoolean()).isFalse();
        JsonNode res = rate(b, a.userId(), "LIKE");
        assertThat(res.get("matched").asBoolean()).isTrue();
        assertThat(res.at("/match/partnerName").asString()).isEqualTo("Иван");
        assertThat(matches(a).get(0).get("partnerName").asString()).isEqualTo("Анна");
        assertThat(matches(b)).hasSize(1);
        verify(bot, timeout(3000)).sendMessage(eq(11L), contains("мэтч"), anyString());
        verify(bot, timeout(3000)).sendMessage(eq(12L), contains("мэтч"), anyString());
    }

    @Test
    @DisplayName("Т-28 повторная оценка той же анкеты не создает дубликатов")
    void repeatedRateIsIdempotent() throws Exception {
        Session a = person(21, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(22, "Анна", 24, "FEMALE", "MALE", 18, 45);
        rate(b, a.userId(), "LIKE");
        rate(a, b.userId(), "LIKE");
        JsonNode again = rate(a, b.userId(), "LIKE");
        assertThat(again.get("matched").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM likes", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Т-29 дизлайк исключает мэтч при встречном лайке")
    void dislikeNoMatch() throws Exception {
        Session a = person(31, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(32, "Анна", 24, "FEMALE", "MALE", 18, 45);
        rate(a, b.userId(), "DISLIKE");
        assertThat(rate(b, a.userId(), "LIKE").get("matched").asBoolean()).isFalse();
        assertThat(matches(a)).isEmpty();
    }

    @Test
    @DisplayName("Т-30 суперлайк: уведомление получателю, отправитель первым в его ленте")
    void superLike() throws Exception {
        Session target = person(41, "Анна", 24, "FEMALE", "MALE", 18, 45);
        Session other = person(42, "Петр", 30, "MALE", "FEMALE", 18, 45);
        Session fan = person(43, "Иван", 25, "MALE", "FEMALE", 18, 45);
        rate(fan, target.userId(), "SUPER_LIKE");
        verify(bot, timeout(3000)).sendMessage(eq(41L), contains("суперлайк"), anyString());
        JsonNode feed = json.readTree(mvc.perform(get("/api/feed").header("Authorization", "Bearer " + target.token()))
                .andReturn().getResponse().getContentAsString());
        assertThat(feed.get(0).get("userId").asLong()).isEqualTo(fan.userId());
        assertThat(feed.get(0).get("superLikedYou").asBoolean()).isTrue();
        assertThat(feed).hasSize(2);
        assertThat(other.userId()).isPositive();
    }

    @Test
    @DisplayName("Т-31 одновременные встречные лайки (50 пар): ровно один мэтч на пару")
    void concurrentMutualLikes() throws Exception {
        int pairs = 50;
        List<long[]> ids = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            Session a = person(1000 + 2L * i, "A" + i, 25, "MALE", "FEMALE", 18, 45);
            Session b = person(1001 + 2L * i, "B" + i, 24, "FEMALE", "MALE", 18, 45);
            ids.add(new long[]{a.userId(), b.userId()});
        }
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (long[] p : ids) {
            fs.add(pool.submit(() -> { start.await(); return matchingService.rate(p[0], p[1], LikeType.LIKE); }));
            fs.add(pool.submit(() -> { start.await(); return matchingService.rate(p[1], p[0], LikeType.LIKE); }));
        }
        start.countDown();
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Integer.class)).isEqualTo(pairs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM likes", Integer.class)).isEqualTo(2 * pairs);
        // каждому участнику — ровно одно уведомление о мэтче
        verify(bot, timeout(10000).times(2 * pairs)).sendMessage(anyLong(), contains("мэтч"), anyString());
    }

    @Test
    @DisplayName("Т-32 разрыв мэтча; чужой мэтч разорвать нельзя")
    void unmatch() throws Exception {
        Session a = person(51, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(52, "Анна", 24, "FEMALE", "MALE", 18, 45);
        Session c = person(53, "Петр", 30, "MALE", "FEMALE", 18, 45);
        rate(a, b.userId(), "LIKE");
        long matchId = rate(b, a.userId(), "LIKE").get("matchId").asLong();
        mvc.perform(delete("/api/matches/" + matchId).header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/matches/" + matchId).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isNoContent());
        assertThat(matches(a)).isEmpty();
        assertThat(matches(b)).isEmpty();
    }

    @Test
    @DisplayName("Т-33 оценка своей или несуществующей анкеты: 400 и 404")
    void rateErrors() throws Exception {
        Session a = person(61, "Иван", 25, "MALE", "FEMALE", 18, 45);
        mvc.perform(post("/api/feed/" + a.userId() + "/rate").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/feed/9999/rate").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/feed/9999/rate").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LOVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Т-34 удаление учетной записи убирает все данные, повторный вход создает нового пользователя")
    void deleteAccountAndReturn() throws Exception {
        Session a = person(71, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(72, "Анна", 24, "FEMALE", "MALE", 18, 45);
        mvc.perform(delete("/api/profile/me").header("Authorization", "Bearer " + b.token()))
                .andExpect(status().isNoContent());
        assertThat(feedIds(a)).isEmpty();
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + b.token()))
                .andExpect(status().isNotFound());
        Session again = login(72, "Анна");
        assertThat(again.userId()).isNotEqualTo(b.userId());
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + again.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Т-35 сбой Telegram Bot API не отменяет сохранение мэтча")
    void telegramFailureKeepsMatch() throws Exception {
        when(bot.sendMessage(anyLong(), anyString(), anyString())).thenReturn(false);
        Session a = person(81, "Иван", 25, "MALE", "FEMALE", 18, 45);
        Session b = person(82, "Анна", 24, "FEMALE", "MALE", 18, 45);
        rate(a, b.userId(), "LIKE");
        assertThat(rate(b, a.userId(), "LIKE").get("matched").asBoolean()).isTrue();
        verify(bot, timeout(3000).times(2)).sendMessage(anyLong(), contains("мэтч"), anyString());
        assertThat(matches(a)).hasSize(1);
    }

    @Test
    @DisplayName("Т-36 команда /start боту: ответ с кнопкой запуска мини-приложения")
    void webhookStart() throws Exception {
        mvc.perform(post("/api/telegram/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_id\":1,\"message\":{\"text\":\"/start\",\"chat\":{\"id\":777}}}"))
                .andExpect(status().isOk());
        verify(bot).sendMessage(eq(777L), anyString(), eq("Открыть знакомства"));
    }
}
