-- Replace global UNIQUE on long_url with per-user uniqueness.
-- Multiple users can now shorten the same URL independently.

DROP INDEX IF EXISTS idx_urls_long_url;

ALTER TABLE urls DROP CONSTRAINT IF EXISTS urls_long_url_key;

CREATE UNIQUE INDEX idx_urls_user_long_url ON urls (user_id, long_url);
