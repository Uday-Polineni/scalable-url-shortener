CREATE TABLE IF NOT EXISTS urls (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(16)  NOT NULL UNIQUE,
    long_url   TEXT         NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_code
    ON urls (code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_long_url
    ON urls (long_url);

