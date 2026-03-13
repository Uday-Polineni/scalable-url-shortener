# Scalable URL Shortener

A production-style URL shortener built with Spring Boot, PostgreSQL, and Redis, focusing on correct HTTP semantics, per-user idempotency, scalable click analytics, and a clean separation between API, business logic, and persistence.

## Features

- **Shorten URLs**
  - `POST /shorten` accepts a JSON body with a `longUrl` and optional `customAlias`.
  - Per-user idempotency: the same user shortening the same URL always returns the same code. Different users get independent short codes.
- **Custom aliases**
  - Optionally specify a `customAlias` (alphanumeric, max 16 chars) instead of an auto-generated code.
- **User accounts & login**
  - Email/password registration and login via Spring Security form login.
  - Session-based authentication for the browser UI.
- **API key authentication**
  - Programmatic clients authenticate via `X-Api-Key` header or `apiKey` query parameter.
  - API keys are generated securely, stored as SHA-256 hashes, and support rotation and revocation.
- **Redirect**
  - `GET /{code}` issues an HTTP **302 Found** redirect to the original URL.
  - Returns **404** when the code is unknown.
- **Click analytics (Redis INCR + periodic flush)**
  - Every redirect increments a counter in Redis (~0.1ms, zero DB writes on the hot path).
  - A background scheduler flushes accumulated counts to Postgres every 30 seconds.
  - Dashboard and stats endpoints combine DB counts with pending Redis counts for real-time accuracy.
- **Validation**
  - Only `http` and `https` URLs are accepted.
  - Optional domain allow-list via configuration.
  - Malformed or blank URLs return **400 Bad Request** with a JSON error.
- **Dashboard**
  - `/ui/dashboard.html` shows total links, total clicks, and the 20 most recent shortened URLs for the logged-in user.
- **Health check**
  - `GET /health` returns a simple JSON status.

## Tech Stack

- **Language**: Java 17+
- **Framework**: Spring Boot 4 (Spring MVC)
- **Persistence**: PostgreSQL 18, Spring Data JPA
- **Migrations**: Flyway (V1–V7)
- **Caching & Counters**: Redis (URL cache + click counters via `INCR`)
- **Security**: Spring Security (session-based login), BCrypt passwords, SHA-256 hashed API keys
- **Scheduling**: Spring `@Scheduled` for periodic click counter flush
- **Build**: Maven (wrapper included: `mvnw` / `mvnw.cmd`)

## Architecture

### High-Level Flow

```
Browser / API Client
        │
        ▼
┌─────────────────────────────────────┐
│         Spring MVC Controllers       │
│  UrlShortenerController              │
│  AuthController, MeController        │
│  ApiKeyAdminController               │
│  HealthController                    │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│          Service Layer               │
│  UrlShortenerService                 │
│  ApiKeyService                       │
│  UrlCacheService                     │
│  ClickFlushScheduler                 │
└──────┬──────────────┬───────────────┘
       │              │
       ▼              ▼
┌────────────┐  ┌───────────┐
│ PostgreSQL │  │   Redis   │
│ (durable)  │  │  (fast)   │
└────────────┘  └───────────┘
```

### Redirect Path (Hot Path — Zero DB Writes)

```
GET /{code}
    │
    ▼
┌─── Redis Cache ─────────────────┐
│ 1. GET url:{code}  → long URL   │  Cache hit? (~0.1ms)
│ 2. INCR clicks:{code}           │  Always increment counter
└─────────────────────────────────┘
    │
    ▼
302 Redirect  ← No database hit on the hot path
```

On a cache miss, the service fetches from Postgres, populates Redis, then redirects.

### Click Counter Flush (Background — Every 30 Seconds)

```
ClickFlushScheduler (@Scheduled fixedRate=30s)
    │
    ▼
┌─── Redis ──────────────────────┐
│ GETDEL clicks:c   → 47         │  Atomically drain all
│ GETDEL clicks:f   → 12         │  pending counters
│ GETDEL clicks:abc → 3          │
└────────────────────────────────┘
    │
    ▼
┌─── PostgreSQL ─────────────────┐
│ UPDATE urls SET                 │
│   redirect_count += 47          │  One atomic UPDATE per code
│   WHERE code = 'c';             │
│ UPDATE urls SET                 │
│   redirect_count += 12          │
│   WHERE code = 'f';             │
└────────────────────────────────┘
```

**Tradeoff**: If Redis crashes, at most ~30 seconds of click data is lost. Redirects are never blocked by DB writes.

### Dashboard Read Path (Real-Time Accurate)

```
GET /me/overview  or  GET /me/links
    │
    ├── Postgres: redirect_count (flushed totals)
    │
    └── Redis: GET clicks:{code} (pending since last flush)
    │
    ▼
    Response: redirect_count + pending = real-time total
```

### Controller Layer

| Controller | Endpoints | Purpose |
|---|---|---|
| `UrlShortenerController` | `POST /shorten`, `GET /{code}`, `GET /stats/{code}` | Shorten, redirect, per-URL stats |
| `AuthController` | `POST /auth/register` | User registration |
| `MeController` | `GET /me/overview`, `GET /me/links` | Dashboard data for logged-in user |
| `ApiKeyAdminController` | `POST /admin/api-keys/users/{userId}`, `POST /admin/api-keys/revoke` | API key lifecycle |
| `HealthController` | `GET /health` | Health check |
| `GlobalExceptionHandler` | — | Maps exceptions to HTTP 400 with JSON error bodies |

### Service Layer

| Service | Responsibility |
|---|---|
| `UrlShortenerService` | URL validation, per-user idempotency, Base62 code generation, resolve + Redis INCR |
| `UrlCacheService` | Redis `url:{code}` cache, `clicks:{code}` counters, `drainAllClickCounts()` |
| `ClickFlushScheduler` | `@Scheduled` — drains Redis click counters into Postgres every 30s |
| `ApiKeyService` | Generate, hash (SHA-256), validate, and revoke API keys |
| `RateLimitService` | Redis fixed-window counter (`INCR` + `EXPIRE`) for per-client rate limiting |

### Persistence Layer

| Entity | Table | Key Columns |
|---|---|---|
| `UrlEntity` | `urls` | `id`, `code` (unique), `long_url`, `user_id` (FK), `redirect_count`, `created_at` |
| `UserEntity` | `users` | `id`, `email`, `name`, `password_hash`, `created_at`, `api_key` (legacy placeholder) |
| `ApiKeyEntity` | `api_keys` | `id`, `user_id` (FK), `key_hash` (unique), `label`, `active`, `created_at`, `revoked_at` |

**Uniqueness**: `(user_id, long_url)` is a composite unique constraint — each user can shorten the same URL once, but different users get independent short codes with independent analytics.

### Database Schema (Flyway Migrations)

| Migration | Description |
|---|---|
| `V1__create_urls_table.sql` | Creates `urls` table with `code` and `long_url` unique indexes |
| `V2__add_users_and_url_user_fk.sql` | Creates `users` table, adds `user_id` FK to `urls` |
| `V3__add_redirect_count.sql` | Adds `redirect_count` column to `urls` |
| `V4__allow_null_code.sql` | Makes `code` nullable (for two-phase insert: save → generate code from ID → update) |
| `V5__create_api_keys_table.sql` | Creates `api_keys` table for secure hashed API key storage |
| `V6__add_password_hash_to_users.sql` | Adds `password_hash` column to `users` |
| `V7__per_user_long_url_uniqueness.sql` | Drops global `UNIQUE` on `long_url`, adds composite `UNIQUE(user_id, long_url)` |

## Running the Application

### Prerequisites

- Java 17 or later
- PostgreSQL running locally (e.g. on `localhost:5432`)
- Redis running locally (e.g. on `localhost:6379`)
- Maven (or use the included wrapper)

### 1. Create the database

Connect to PostgreSQL and run:

```sql
CREATE DATABASE url_shortener;
```

### 2. Configure credentials

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/url_shortener
spring.datasource.username=postgres
spring.datasource.password=your_password

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true

spring.flyway.enabled=true

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. Start the application

```bash
.\mvnw.cmd spring-boot:run
```

On startup, Flyway applies all pending migrations and validates the schema.

### 4. Use the web UI

| Page | URL | Purpose |
|---|---|---|
| Register | `http://localhost:8080/ui/register.html` | Create a new account |
| Login | `http://localhost:8080/ui/login.html` | Log in with email/password |
| Shortener | `http://localhost:8080/ui/shortener.html` | Create and test short URLs |
| Dashboard | `http://localhost:8080/ui/dashboard.html` | View your stats and recent links |

## API Usage

### Health

```http
GET /health
```

```json
{ "status": "ok" }
```

### Register a user

```http
POST /auth/register
Content-Type: application/json

{
  "email": "you@example.com",
  "name": "Your Name",
  "password": "securepass123"
}
```

### Create a short URL

```http
POST /shorten
Content-Type: application/json
X-Api-Key: your_api_key

{
  "longUrl": "https://example.com",
  "customAlias": "my-link"
}
```

- `customAlias` is optional (alphanumeric, max 16 chars).
- From the browser UI, the session cookie is used automatically — no API key needed.
- Repeating the same request returns the **same** short code (per-user idempotent).
- Invalid URLs return **400 Bad Request** with a JSON error.

**Response (200 OK):**

```json
{
  "shortUrl": "http://localhost:8080/my-link"
}
```

### Redirect

```http
GET /my-link
```

- **302 Found** with `Location: https://example.com` when the code exists.
- **404 Not Found** when the code does not exist.

### URL stats

```http
GET /stats/my-link
```

```json
{
  "code": "my-link",
  "longUrl": "https://example.com",
  "redirectCount": 42,
  "createdAt": "2026-03-02T10:30:00Z"
}
```

### Dashboard APIs (session-authenticated)

```http
GET /me/overview    → { "email": "...", "totalLinks": 5, "totalClicks": 123 }
GET /me/links       → [ { "code": "...", "longUrl": "...", "redirectCount": 42, "createdAt": "..." }, ... ]
```

### API key management (session-authenticated)

```http
POST /admin/api-keys/users/1?label=ci-bot  → Creates a new API key for user 1 (returns the raw key once)
POST /admin/api-keys/revoke?apiKey=<raw>    → Revokes the given API key
```

## Rate Limiting & Abuse Protection

All endpoints are protected by a Redis-backed rate limiter (`RateLimitFilter`). Each client is identified by authenticated email (if logged in) or IP address (anonymous). Limits are enforced using a fixed-window counter per minute.

### Default limits

| Bucket | Endpoint | Limit |
|--------|----------|-------|
| `shorten` | `POST /shorten` | 20 req/min |
| `register` | `POST /auth/register` | 5 req/min |
| `login` | `POST /login` | 10 req/min |
| `redirect` | `GET /{code}` | 100 req/min |
| `stats` | `GET /stats/{code}` | 30 req/min |
| `general` | Everything else | 60 req/min |

When a client exceeds their limit, the server responds with:

```json
HTTP 429 Too Many Requests

{ "error": "Rate limit exceeded. Try again later." }
```

### How it works

```
Incoming request
    │
    ├── Identify client: email (if authenticated) or IP (X-Forwarded-For / remoteAddr)
    ├── Determine bucket: shorten / register / login / redirect / stats / general
    │
    ▼
┌─── Redis ──────────────────────────┐
│ Key: rl:{clientId}:{bucket}:{min}  │
│ INCR → count                       │
│ If count == 1 → EXPIRE 60s         │
└────────────────────────────────────┘
    │
    ├── count ≤ limit → proceed
    └── count > limit → 429 Too Many Requests
```

### Configuration

All limits are configurable in `application.properties`:

```properties
urlshortener.rate-limit.shorten-per-minute=20
urlshortener.rate-limit.register-per-minute=5
urlshortener.rate-limit.login-per-minute=10
urlshortener.rate-limit.redirect-per-minute=100
urlshortener.rate-limit.stats-per-minute=30
urlshortener.rate-limit.general-per-minute=60
```

If Redis is unavailable, the rate limiter **fails open** (allows the request) to avoid blocking legitimate traffic.

## Security Model

| Mechanism | Used by | How it works |
|---|---|---|
| **Session login** | Browser UI | Email/password → Spring Security session cookie |
| **API keys** | Programmatic clients | `X-Api-Key` header → SHA-256 hash lookup in `api_keys` table |
| **BCrypt** | Password storage | Passwords hashed with BCrypt before storage |
| **SHA-256** | API key storage | Raw API keys are never stored; only their SHA-256 hashes |

### Endpoint access rules

| Pattern | Access |
|---|---|
| `/health`, `/auth/register`, `/ui/**`, `/{code}` | Public |
| `/shorten`, `/stats/**` | Public (but requires auth to create) |
| `/me/**` | Authenticated users only |
| `/admin/**` | Authenticated users only |

## Development Notes

- The application layer is stateless and can be scaled horizontally behind a load balancer.
- All persistent state lives in PostgreSQL via JPA.
- Per-user idempotency is enforced by both the service layer and a composite `UNIQUE(user_id, long_url)` database constraint.
- Redis serves two roles: URL lookup cache (`url:{code}`) and click counters (`clicks:{code}`).
- Click counting uses Redis `INCR` for zero-latency redirects, with a `@Scheduled` flush to Postgres every 30 seconds.
- Dashboard reads combine flushed DB counts with pending Redis counts for real-time accuracy.
- The `ClickFlushScheduler` uses `GETDEL` for atomic drain — no double-counting even under concurrent flushes.
- Session-based login and API keys provide two independent authentication paths for browser and programmatic clients respectively.
- Redis-backed rate limiting protects all endpoints with configurable per-minute limits per client. Fails open if Redis is down.
- Request body size is capped at 1MB to prevent payload abuse.
- The `users.api_key` column is a legacy placeholder (populated with a random UUID on registration) retained for schema compatibility; all real API key validation uses the `api_keys` table with SHA-256 hashes.
