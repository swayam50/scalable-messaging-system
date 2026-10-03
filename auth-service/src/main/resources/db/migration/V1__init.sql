-- Accounts
CREATE TABLE users (
    id            VARCHAR(26)  PRIMARY KEY,           -- ULID
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Case-insensitive uniqueness: "Swayam" and "swayam" are the same user
CREATE UNIQUE INDEX ux_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX ux_users_email_lower    ON users (LOWER(email));

-- Refresh tokens (only the SHA-256 hash is stored)
CREATE TABLE refresh_tokens (
    id          VARCHAR(26) PRIMARY KEY,              -- ULID
    user_id     VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
