-- Схема БД веб-приложения для знакомств
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    telegram_id    BIGINT      NOT NULL UNIQUE,
    username       VARCHAR(64),
    first_name     VARCHAR(128),
    last_name      VARCHAR(128),
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'HIDDEN', 'DEACTIVATED')),
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    last_active_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE profiles (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    display_name     VARCHAR(64)  NOT NULL,
    birth_date       DATE         NOT NULL,
    gender           VARCHAR(8)   NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    bio              VARCHAR(500),
    city             VARCHAR(64),
    preferred_gender VARCHAR(8)   NOT NULL DEFAULT 'ANY' CHECK (preferred_gender IN ('MALE', 'FEMALE', 'ANY')),
    min_age          INT          NOT NULL DEFAULT 18 CHECK (min_age >= 18),
    max_age          INT          NOT NULL DEFAULT 45 CHECK (max_age <= 99),
    CONSTRAINT chk_age_range CHECK (min_age <= max_age)
);
CREATE INDEX idx_profiles_search ON profiles (gender, birth_date);

CREATE TABLE photos (
    id          BIGSERIAL PRIMARY KEY,
    profile_id  BIGINT      NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    storage_key VARCHAR(64) NOT NULL UNIQUE,
    position    INT         NOT NULL CHECK (position BETWEEN 0 AND 5),
    uploaded_at TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_photos_profile ON photos (profile_id);

CREATE TABLE interests (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(32) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL
);

CREATE TABLE profile_interests (
    profile_id  BIGINT NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    interest_id BIGINT NOT NULL REFERENCES interests (id),
    PRIMARY KEY (profile_id, interest_id)
);

CREATE TABLE likes (
    id           BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    to_user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(16) NOT NULL CHECK (type IN ('LIKE', 'DISLIKE', 'SUPER_LIKE')),
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_likes_pair UNIQUE (from_user_id, to_user_id),
    CONSTRAINT chk_likes_not_self CHECK (from_user_id <> to_user_id)
);
CREATE INDEX idx_likes_to ON likes (to_user_id, type);

CREATE TABLE matches (
    id           BIGSERIAL PRIMARY KEY,
    user_low_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_high_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'UNMATCHED')),
    matched_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_matches_pair UNIQUE (user_low_id, user_high_id),
    CONSTRAINT chk_matches_order CHECK (user_low_id < user_high_id)
);
CREATE INDEX idx_matches_high ON matches (user_high_id);
