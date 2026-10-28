package ru.langsot.dating.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Фотография анкеты. Файл хранится в хранилище, в БД — только ключ. */
@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "storage_key", nullable = false, unique = true, length = 64)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    protected Photo() {
    }

    Photo(Profile profile, String storageKey, int position) {
        this.profile = profile;
        this.storageKey = storageKey;
        this.position = position;
    }

    public String getUrl() {
        return "/api/photos/" + storageKey;
    }

    void setPosition(int position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getPosition() {
        return position;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
