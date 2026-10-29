package ru.langsot.dating.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.langsot.dating.domain.Match;
import ru.langsot.dating.domain.User;
import ru.langsot.dating.repository.MatchRepository;
import ru.langsot.dating.repository.ProfileRepository;
import ru.langsot.dating.repository.UserRepository;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NotificationService — уведомления о мэтчах")
class NotificationServiceTest {

    private static User user(long id) throws Exception {
        User u = new User(100 + id, "u" + id, "Имя" + id, null);
        Field f = User.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, id);
        return u;
    }

    @Test
    @DisplayName("Т-37 участник мэтча удален до отправки: уведомление пропускается без ошибки")
    void missingParticipant() throws Exception {
        TelegramBotClient bot = mock(TelegramBotClient.class);
        UserRepository users = mock(UserRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        User a = user(1);
        User b = user(2);
        when(users.findById(1L)).thenReturn(Optional.of(a));
        when(users.findById(2L)).thenReturn(Optional.empty());
        NotificationService s = new NotificationService(bot, mock(MatchRepository.class), users, profiles);

        assertThatCode(() -> s.notifyMatch(new Match(a, b))).doesNotThrowAnyException();
        verify(bot, never()).sendMessage(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("уведомления отправляются обоим участникам по их идентификаторам Telegram")
    void bothNotified() throws Exception {
        TelegramBotClient bot = mock(TelegramBotClient.class);
        UserRepository users = mock(UserRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        User a = user(1);
        User b = user(2);
        when(users.findById(1L)).thenReturn(Optional.of(a));
        when(users.findById(2L)).thenReturn(Optional.of(b));
        when(profiles.findByUserId(anyLong())).thenReturn(Optional.empty());
        new NotificationService(bot, mock(MatchRepository.class), users, profiles).notifyMatch(new Match(a, b));
        verify(bot).sendMessage(eq(101L), contains("Имя2"), anyString());
        verify(bot).sendMessage(eq(102L), contains("Имя1"), anyString());
    }
}
