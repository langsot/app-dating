package ru.langsot.dating.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Параметры поиска анкеты (встраиваемый объект). */
@Embeddable
public class SearchPreferences {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 99;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_gender", nullable = false, length = 8)
    private GenderPreference preferredGender = GenderPreference.ANY;

    @Column(name = "min_age", nullable = false)
    private int minAge = MIN_AGE;

    @Column(name = "max_age", nullable = false)
    private int maxAge = 45;

    protected SearchPreferences() {
    }

    public SearchPreferences(GenderPreference preferredGender, int minAge, int maxAge) {
        if (preferredGender == null) {
            throw new IllegalArgumentException("Не указан предпочитаемый пол");
        }
        if (minAge < MIN_AGE || maxAge > MAX_AGE || minAge > maxAge) {
            throw new IllegalArgumentException("Недопустимый диапазон возраста: " + minAge + "–" + maxAge);
        }
        this.preferredGender = preferredGender;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public static SearchPreferences defaults() {
        return new SearchPreferences();
    }

    /** Подходит ли анкета под параметры поиска. */
    public boolean accepts(Profile candidate) {
        int age = candidate.getAge();
        return preferredGender.accepts(candidate.getGender()) && age >= minAge && age <= maxAge;
    }

    public GenderPreference getPreferredGender() {
        return preferredGender;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }
}
