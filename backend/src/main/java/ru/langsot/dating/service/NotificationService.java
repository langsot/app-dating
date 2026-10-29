package ru.langsot.dating.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.langsot.dating.domain.Match;
import ru.langsot.dating.domain.Profile;
import ru.langsot.dating.domain.User;
import ru.langsot.dating.repository.MatchRepository;
import ru.langsot.dating.repository.ProfileRepository;
import ru.langsot.dating.repository.UserRepository;
import ru.langsot.dating.service.MatchingService.MatchCreatedEvent;
import ru.langsot.dating.service.MatchingService.SuperLikeEvent;

/**
 * Уведомления пользователей через бота. Вызывается после фиксации транзакции,
 * поэтому сбой Telegram Bot API не отменяет сохранение оценки и мэтча.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TelegramBotClient bot;
    private final MatchRepository matches;
    private final UserRepository users;
    private final ProfileRepository profiles;

    public NotificationService(TelegramBotClient bot, MatchRepository matches, UserRepository users,
                               ProfileRepository profiles) {
        this.bot = bot;
        this.matches = matches;
        this.users = users;
        this.profiles = profiles;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatch(MatchCreatedEvent event) {
        matches.findById(event.matchId()).ifPresent(this::notifyMatch);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuperLike(SuperLikeEvent event) {
        users.findById(event.toUserId()).ifPresent(to ->
                bot.sendMessage(to.getTelegramId(), "Кто-то поставил вам суперлайк ⭐ Загляните в ленту!", "Открыть ленту"));
    }

    void notifyMatch(Match m) {
        // Уведомление выполняется асинхронно: к этому моменту записи пользователей могли быть удалены.
        User a = users.findById(m.getUserLow().getId()).orElse(null);
        User b = users.findById(m.getUserHigh().getId()).orElse(null);
        if (a == null || b == null) {
            log.warn("Мэтч {}: участник не найден, уведомление пропущено", m.getId());
            return;
        }
        boolean sentA = bot.sendMessage(a.getTelegramId(), "У вас новый мэтч с " + nameOf(b) + "! 💙", "Открыть мэтчи");
        boolean sentB = bot.sendMessage(b.getTelegramId(), "У вас новый мэтч с " + nameOf(a) + "! 💙", "Открыть мэтчи");
        log.info("Мэтч {}: уведомления пользователям {} и {} — {}", m.getId(), a.getId(), b.getId(),
                sentA && sentB ? "доставлены" : "не все доставлены (мэтч сохранен)");
    }

    private String nameOf(User u) {
        return profiles.findByUserId(u.getId()).map(Profile::getDisplayName).orElse(u.getFirstName());
    }
}
