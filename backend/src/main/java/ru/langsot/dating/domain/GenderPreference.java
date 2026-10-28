package ru.langsot.dating.domain;

public enum GenderPreference {
    MALE, FEMALE, ANY;

    /** Проверяет, подходит ли пол кандидата под предпочтение. */
    public boolean accepts(Gender gender) {
        return this == ANY || (gender != null && name().equals(gender.name()));
    }
}
