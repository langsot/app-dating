package ru.langsot.dating.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Учетная запись пользователя, созданная по данным Telegram. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    protected User() {
    }

    public User(Long telegramId, String username, String firstName, String lastName) {
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void hide() {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Учетная запись деактивирована");
        }
        status = UserStatus.HIDDEN;
    }

    public void show() {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Учетная запись деактивирована");
        }
        status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        status = UserStatus.DEACTIVATED;
    }

    /** Повторный вход деактивированного пользователя возвращает учетную запись в ленту. */
    public void reactivate() {
        if (status == UserStatus.DEACTIVATED) {
            status = UserStatus.ACTIVE;
        }
    }

    /** Обновляет время последней активности. */
    public void touch() {
        lastActiveAt = LocalDateTime.now();
    }

    public void updateFromTelegram(String username, String firstName, String lastName) {
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Long getId() {
        return id;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
}
