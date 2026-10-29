package ru.langsot.dating.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.langsot.dating.domain.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeedService.buildQuery — параметры подбора анкет")
class FeedQueryTest {

    @Test
    @DisplayName("Т-19 возраст 20–30 переводится в границы даты рождения, пол ANY — в оба пола")
    void bounds() throws Exception {
        User u = new User(1L, "u", "U", null);
        Field f = User.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, 5L);
        Profile p = new Profile(u, "Иван", LocalDate.now().minusYears(25), Gender.MALE);
        p.setPreferences(new SearchPreferences(GenderPreference.ANY, 20, 30));
        LocalDate today = LocalDate.of(2026, 10, 27);

        FeedService.FeedQuery q = FeedService.buildQuery(p, today);

        assertThat(q.viewerId()).isEqualTo(5L);
        assertThat(q.genders()).isEqualTo(List.of("MALE", "FEMALE"));
        // 20 лет исполнилось не позже сегодняшнего дня, 31 год еще не исполнился
        assertThat(q.bornBefore()).isEqualTo(LocalDate.of(2006, 10, 27));
        assertThat(q.bornAfter()).isEqualTo(LocalDate.of(1995, 10, 27));
        assertThat(q.viewerGender()).isEqualTo("MALE");
        assertThat(q.viewerAge()).isEqualTo(25);
    }
}
