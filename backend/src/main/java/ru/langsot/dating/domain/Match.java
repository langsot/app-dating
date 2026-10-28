package ru.langsot.dating.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Взаимная симпатия двух пользователей.
 * Участники хранятся упорядоченно (меньший и больший id), что вместе с ограничением
 * уникальности в БД исключает повторный мэтч одной пары.
 */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_low_id", nullable = false)
    private User userLow;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_high_id", nullable = false)
    private User userHigh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchStatus status = MatchStatus.ACTIVE;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt = LocalDateTime.now();

    protected Match() {
    }

    public Match(User a, User b) {
        if (Objects.equals(a.getId(), b.getId())) {
            throw new IllegalArgumentException("Мэтч требует двух разных пользователей");
        }
        boolean aFirst = a.getId() < b.getId();
        this.userLow = aFirst ? a : b;
        this.userHigh = aFirst ? b : a;
    }

    public boolean involves(Long userId) {
        return userLow.getId().equals(userId) || userHigh.getId().equals(userId);
    }

    public User partnerOf(Long userId) {
        if (userLow.getId().equals(userId)) {
            return userHigh;
        }
        if (userHigh.getId().equals(userId)) {
            return userLow;
        }
        throw new IllegalArgumentException("Пользователь не участвует в мэтче");
    }

    public void unmatch() {
        status = MatchStatus.UNMATCHED;
    }

    public boolean isActive() {
        return status == MatchStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public User getUserLow() {
        return userLow;
    }

    public User getUserHigh() {
        return userHigh;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public LocalDateTime getMatchedAt() {
        return matchedAt;
    }
}
