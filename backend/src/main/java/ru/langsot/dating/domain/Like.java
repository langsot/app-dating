package ru.langsot.dating.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/** Оценка анкеты одного пользователя другим. */
@Entity
@Table(name = "likes")
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User from;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User to;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LikeType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Like() {
    }

    public Like(User from, User to, LikeType type) {
        if (Objects.equals(from.getId(), to.getId())) {
            throw new IllegalArgumentException("Нельзя оценить собственную анкету");
        }
        this.from = from;
        this.to = to;
        this.type = Objects.requireNonNull(type);
    }

    public boolean isPositive() {
        return type.isPositive();
    }

    public Long getId() {
        return id;
    }

    public User getFrom() {
        return from;
    }

    public User getTo() {
        return to;
    }

    public LikeType getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
