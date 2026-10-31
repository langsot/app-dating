package ru.langsot.dating.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.domain.*;
import ru.langsot.dating.dto.Dtos.FeedCardDto;
import ru.langsot.dating.exception.ConflictException;
import ru.langsot.dating.repository.LikeRepository;
import ru.langsot.dating.repository.ProfileRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Подбор анкет для ленты (ВИ-3). */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private final ProfileRepository profiles;
    private final LikeRepository likes;
    private final UserService userService;
    private final Clock clock;
    private final int pageSize;

    public FeedService(ProfileRepository profiles, LikeRepository likes, UserService userService,
                       Clock clock, AppProperties props) {
        this.profiles = profiles;
        this.likes = likes;
        this.userService = userService;
        this.clock = clock;
        this.pageSize = props.feed().pageSize();
    }

    /** Параметры запроса кандидатов (объект-значение FeedQuery). */
    public record FeedQuery(Long viewerId, List<String> genders, LocalDate bornBefore, LocalDate bornAfter,
                            String viewerGender, int viewerAge) {
    }

    @Transactional
    public List<FeedCardDto> getNextCards(Long userId, Integer limit) {
        User viewer = userService.get(userId);
        Profile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new ConflictException("Сначала заполните анкету"));
        if (!profile.isComplete()) {
            throw new ConflictException("Добавьте хотя бы одну фотографию, чтобы смотреть анкеты");
        }
        viewer.touch();
        int lim = limit == null ? pageSize : Math.max(1, Math.min(limit, 50));
        FeedQuery q = buildQuery(profile, LocalDate.now(clock));
        List<Profile> found = profiles.findCandidates(q.viewerId(), q.genders(), q.bornBefore(), q.bornAfter(),
                q.viewerGender(), q.viewerAge(), lim);
        if (found.isEmpty()) {
            log.info("Лента: для пользователя {} нет подходящих анкет", userId);
            return List.of();
        }
        // Признак суперлайка — одним запросом для всей пачки; фото и интересы догружаются
        // пакетно (hibernate.default_batch_fetch_size), а не отдельным запросом на каждую карточку.
        Set<Long> superLikers = likes.findSuperLikers(userId, found.stream().map(p -> p.getUser().getId()).toList());
        log.info("Лента: пользователю {} выдано карточек: {}", userId, found.size());
        return found.stream()
                .map(p -> DtoMapper.toCard(p, superLikers.contains(p.getUser().getId())))
                .toList();
    }

    /**
     * Возраст кандидата в диапазоне [minAge; maxAge] эквивалентен дате рождения
     * в интервале (today − (maxAge + 1) лет; today − minAge лет].
     */
    static FeedQuery buildQuery(Profile viewer, LocalDate today) {
        SearchPreferences pref = viewer.getPreferences();
        List<String> genders = switch (pref.getPreferredGender()) {
            case MALE -> List.of(Gender.MALE.name());
            case FEMALE -> List.of(Gender.FEMALE.name());
            case ANY -> List.of(Gender.MALE.name(), Gender.FEMALE.name());
        };
        return new FeedQuery(viewer.getUser().getId(), genders,
                today.minusYears(pref.getMinAge()), today.minusYears(pref.getMaxAge() + 1L),
                viewer.getGender().name(), viewer.getAge());
    }
}
