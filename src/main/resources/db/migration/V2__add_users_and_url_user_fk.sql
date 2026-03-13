CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    api_key    VARCHAR(64)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_api_key
    ON users (api_key);

ALTER TABLE urls
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

ALTER TABLE urls
    ADD CONSTRAINT IF NOT EXISTS fk_urls_user
        FOREIGN KEY (user_id)
        REFERENCES users (id);

