package ru.langsot.dating.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.langsot.dating.domain.*;
import ru.langsot.dating.dto.Dtos.MatchDto;
import ru.langsot.dating.dto.Dtos.RateResult;
import ru.langsot.dating.exception.BadRequestException;
import ru.langsot.dating.exception.NotFoundException;
import ru.langsot.dating.repository.LikeRepository;
import ru.langsot.dating.repository.MatchRepository;
import ru.langsot.dating.repository.ProfileRepository;
import ru.langsot.dating.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/** Оценка анкет и образование мэтчей (ВИ-4, ВИ-5). */
@Service
public class MatchingService {

    private final UserRepository users;
    private final LikeRepository likes;
    private final MatchRepository matches;
    private final ProfileRepository profiles;
    private final ApplicationEventPublisher events;

    public MatchingService(UserRepository users, LikeRepository likes, MatchRepository matches,
                           ProfileRepository profiles, ApplicationEventPublisher events) {
        this.users = users;
        this.likes = likes;
        this.matches = matches;
        this.profiles = profiles;
        this.events = events;
    }

    /** Событие образования мэтча; обрабатывается после фиксации транзакции. */
    public record MatchCreatedEvent(Long matchId) {
    }

    /** Событие получения суперлайка. */
    public record SuperLikeEvent(Long fromUserId, Long toUserId) {
    }

    /**
     * Сохраняет оценку и при встречной положительной оценке создает мэтч.
     * Строки обоих пользователей блокируются в порядке возрастания id, поэтому
     * одновременные встречные лайки обрабатываются последовательно и мэтч создается ровно один раз.
     */
    @Transactional
    public RateResult rate(Long fromId, Long toId, LikeType type) {
        if (fromId.equals(toId)) {
            throw new BadRequestException("Нельзя оценить собственную анкету");
        }
        User first = users.lockById(Math.min(fromId, toId))
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        User second = users.lockById(Math.max(fromId, toId))
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        User from = first.getId().equals(fromId) ? first : second;
        User to = from == first ? second : first;
        if (!to.isActive() || profiles.findByUserId(toId).isEmpty()) {
            throw new NotFoundException("Анкета недоступна");
        }

        Optional<Like> existing = likes.findByFromIdAndToId(fromId, toId);
        if (existing.isPresent()) {
            // Повторная оценка (например, двойное нажатие) не меняет результат.
            return currentResult(fromId, toId);
        }
        likes.save(new Like(from, to, type));
        from.touch();
        if (type == LikeType.SUPER_LIKE) {
            events.publishEvent(new SuperLikeEvent(fromId, toId));
        }
        if (!type.isPositive() || !likes.existsPositive(toId, fromId)) {
            return RateResult.noMatch();
        }
        Match match = matches.findByPair(first.getId(), second.getId())
                .orElseGet(() -> {
                    Match created = matches.save(new Match(from, to));
                    events.publishEvent(new MatchCreatedEvent(created.getId()));
                    return created;
                });
        return new RateResult(true, match.getId(), toDto(match, fromId));
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getMatches(Long userId) {
        return matches.findActiveByUserId(userId).stream().map(m -> toDto(m, userId)).toList();
    }

    @Transactional
    public void unmatch(Long userId, Long matchId) {
        Match match = matches.findById(matchId)
                .filter(m -> m.involves(userId))
                .orElseThrow(() -> new NotFoundException("Мэтч не найден"));
        match.unmatch();
    }

    private RateResult currentResult(Long fromId, Long toId) {
        return matches.findByPair(Math.min(fromId, toId), Math.max(fromId, toId))
                .filter(Match::isActive)
                .map(m -> new RateResult(true, m.getId(), toDto(m, fromId)))
                .orElse(RateResult.noMatch());
    }

    private MatchDto toDto(Match m, Long viewerId) {
        return DtoMapper.toDto(m, viewerId, profiles.findByUserId(m.partnerOf(viewerId).getId()));
    }
}
