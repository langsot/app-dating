package ru.langsot.dating.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.langsot.dating.dto.Dtos.AuthResponse;
import ru.langsot.dating.repository.ProfileRepository;
import ru.langsot.dating.domain.Profile;

/** Авторизация пользователя по данным Telegram Mini App. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final InitDataValidator validator;
    private final UserService userService;
    private final JwtTokenProvider tokens;
    private final ProfileRepository profiles;

    public AuthService(InitDataValidator validator, UserService userService, JwtTokenProvider tokens,
                       ProfileRepository profiles) {
        this.validator = validator;
        this.userService = userService;
        this.tokens = tokens;
        this.profiles = profiles;
    }

    @Transactional
    public AuthResponse authenticate(String initData) {
        InitDataValidator.TelegramInitData data = validator.validate(initData);
        UserService.FindOrCreateResult result = userService.findOrCreate(data.user());
        Long userId = result.user().getId();
        boolean complete = profiles.findByUserId(userId).map(Profile::isComplete).orElse(false);
        log.info("Вход: пользователь {}, новый: {}, анкета заполнена: {}", userId, result.created(), complete);
        return new AuthResponse(tokens.createToken(userId), result.created(), complete, userId);
    }
}
