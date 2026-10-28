package ru.langsot.dating.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Правила предметной области (классы-сущности)")
class DomainRulesTest {

    private static User user(long id) {
        User u = new User(1000 + id, "u" + id, "Имя" + id, null);
        try {
            Field f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return u;
    }

    private static Profile profile(int age) {
        return new Profile(user(1), "Анна", LocalDate.now().minusYears(age).minusDays(1), Gender.FEMALE);
    }

    @Test
    @DisplayName("Т-10 анкета пользователя младше 18 лет не создается")
    void underage() {
        assertThatThrownBy(() -> profile(17)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18");
        assertThat(profile(18).getAge()).isEqualTo(18);
    }

    @Test
    @DisplayName("Т-11 не более 6 фотографий; после удаления позиции перенумерованы")
    void photosLimit() throws Exception {
        Profile p = profile(25);
        for (int i = 0; i < 6; i++) {
            Photo ph = p.addPhoto("k" + i);
            Field f = Photo.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ph, (long) i);
        }
        assertThatThrownBy(() -> p.addPhoto("k7")).isInstanceOf(IllegalStateException.class);
        p.removePhoto(0L);
        assertThat(p.getPhotos()).extracting(Photo::getPosition).containsExactly(0, 1, 2, 3, 4);
        assertThat(p.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Т-12 описание длиннее 500 символов и более 10 интересов отклоняются")
    void bioAndInterests() {
        Profile p = profile(25);
        assertThatThrownBy(() -> p.update("Анна", p.getBirthDate(), Gender.FEMALE, "x".repeat(501), null))
                .isInstanceOf(IllegalArgumentException.class);
        Set<Interest> many = new HashSet<>();
        for (int i = 0; i < 11; i++) {
            many.add(new Interest("i" + i, "c"));
        }
        assertThatThrownBy(() -> p.setInterests(many)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "Т-13 диапазон возраста {0}–{1}: допустим = {2}")
    @CsvSource({"18,45,true", "17,30,false", "30,25,false", "18,99,true", "20,100,false"})
    void ageRange(int min, int max, boolean ok) {
        if (ok) {
            assertThat(new SearchPreferences(GenderPreference.ANY, min, max).getMinAge()).isEqualTo(min);
        } else {
            assertThatThrownBy(() -> new SearchPreferences(GenderPreference.ANY, min, max))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Т-14 мэтч: упорядочение участников и поиск собеседника")
    void matchPartner() {
        User a = user(7);
        User b = user(3);
        Match m = new Match(a, b);
        assertThat(m.getUserLow().getId()).isEqualTo(3);
        assertThat(m.getUserHigh().getId()).isEqualTo(7);
        assertThat(m.partnerOf(7L).getId()).isEqualTo(3);
        assertThat(m.involves(5L)).isFalse();
        assertThatThrownBy(() -> new Match(a, a)).isInstanceOf(IllegalArgumentException.class);
        m.unmatch();
        assertThat(m.isActive()).isFalse();
    }

    @Test
    @DisplayName("деактивированную учетную запись нельзя скрыть, повторный вход ее восстанавливает")
    void userStates() {
        User u = user(1);
        u.hide();
        assertThat(u.getStatus()).isEqualTo(UserStatus.HIDDEN);
        u.deactivate();
        assertThatThrownBy(u::hide).isInstanceOf(IllegalStateException.class);
        u.reactivate();
        assertThat(u.isActive()).isTrue();
    }
}
