package ru.langsot.dating.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.langsot.dating.domain.User;
import ru.langsot.dating.exception.NotFoundException;
import ru.langsot.dating.repository.UserRepository;
import ru.langsot.dating.service.InitDataValidator.TelegramUser;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public record FindOrCreateResult(User user, boolean created) {
    }

    /** Находит пользователя по идентификатору Telegram или создает нового. */
    @Transactional
    public FindOrCreateResult findOrCreate(TelegramUser tg) {
        return users.findByTelegramId(tg.id())
                .map(u -> {
                    u.updateFromTelegram(tg.username(), tg.firstName(), tg.lastName());
                    u.reactivate();
                    u.touch();
                    return new FindOrCreateResult(u, false);
                })
                .orElseGet(() -> new FindOrCreateResult(
                        users.save(new User(tg.id(), tg.username(), tg.firstName(), tg.lastName())), true));
    }

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    @Transactional
    public void setHidden(Long userId, boolean hidden) {
        User user = get(userId);
        if (hidden) {
            user.hide();
        } else {
            user.show();
        }
    }

    @Transactional
    public void deactivate(Long userId) {
        get(userId).deactivate();
    }
}
