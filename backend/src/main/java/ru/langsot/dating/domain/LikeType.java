package ru.langsot.dating.domain;

public enum LikeType {
    LIKE, DISLIKE, SUPER_LIKE;

    /** Положительная оценка участвует в образовании мэтча. */
    public boolean isPositive() {
        return this != DISLIKE;
    }
}
