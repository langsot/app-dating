package ru.langsot.dating.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/** Анкета пользователя. */
@Entity
@Table(name = "profiles")
public class Profile {

    public static final int MAX_PHOTOS = 6;
    public static final int MAX_INTERESTS = 10;
    public static final int MAX_BIO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Gender gender;

    @Column(length = MAX_BIO)
    private String bio;

    @Column(length = 64)
    private String city;

    @Embedded
    private SearchPreferences preferences = SearchPreferences.defaults();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Photo> photos = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "profile_interests",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "interest_id"))
    private Set<Interest> interests = new LinkedHashSet<>();

    protected Profile() {
    }

    public Profile(User user, String displayName, LocalDate birthDate, Gender gender) {
        this.user = Objects.requireNonNull(user);
        update(displayName, birthDate, gender, null, null);
    }

    /** Изменяет основные сведения анкеты с проверкой правил предметной области. */
    public void update(String displayName, LocalDate birthDate, Gender gender, String bio, String city) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Имя не может быть пустым");
        }
        if (birthDate == null || ageAt(birthDate, LocalDate.now()) < SearchPreferences.MIN_AGE) {
            throw new IllegalArgumentException("Пользователь должен быть старше 18 лет");
        }
        if (gender == null) {
            throw new IllegalArgumentException("Не указан пол");
        }
        if (bio != null && bio.length() > MAX_BIO) {
            throw new IllegalArgumentException("Описание длиннее " + MAX_BIO + " символов");
        }
        this.displayName = displayName.trim();
        this.birthDate = birthDate;
        this.gender = gender;
        this.bio = bio == null ? null : bio.trim();
        this.city = city == null || city.isBlank() ? null : city.trim();
    }

    public int getAge() {
        return ageAt(birthDate, LocalDate.now(Clock.systemDefaultZone()));
    }

    static int ageAt(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }

    /** Анкета заполнена, если есть хотя бы одна фотография. */
    public boolean isComplete() {
        return displayName != null && birthDate != null && gender != null && !photos.isEmpty();
    }

    public Photo addPhoto(String storageKey) {
        if (photos.size() >= MAX_PHOTOS) {
            throw new IllegalStateException("Можно загрузить не более " + MAX_PHOTOS + " фотографий");
        }
        Photo photo = new Photo(this, storageKey, photos.size());
        photos.add(photo);
        return photo;
    }

    public Photo removePhoto(Long photoId) {
        Photo photo = photos.stream().filter(p -> Objects.equals(p.getId(), photoId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Фотография не найдена"));
        photos.remove(photo);
        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setPosition(i);
        }
        return photo;
    }

    public void setInterests(Set<Interest> newInterests) {
        if (newInterests.size() > MAX_INTERESTS) {
            throw new IllegalArgumentException("Можно выбрать не более " + MAX_INTERESTS + " интересов");
        }
        interests.clear();
        interests.addAll(newInterests);
    }

    public void setPreferences(SearchPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public String getBio() {
        return bio;
    }

    public String getCity() {
        return city;
    }

    public SearchPreferences getPreferences() {
        return preferences;
    }

    public List<Photo> getPhotos() {
        return Collections.unmodifiableList(photos);
    }

    public Set<Interest> getInterests() {
        return Collections.unmodifiableSet(interests);
    }
}
