# Symfony 7 skeleton for HabitTracker API

This folder contains a minimal skeleton and config stubs to bootstrap the server.
Target stack: Symfony 7, Doctrine ORM (PostgreSQL), LexikJWTAuthenticationBundle, stateless REST API.

## 1) Create project
```bash
composer create-project symfony/skeleton habittracker-api
cd habittracker-api
composer require symfony/framework-bundle symfony/runtime symfony/yaml symfony/validator symfony/serializer symfony/security-bundle
composer require doctrine orm maker annotations
composer require nelmio/cors-bundle
composer require lexik/jwt-authentication-bundle
```

## 2) Configure .env
- DATABASE_URL for PostgreSQL
- CORS_ALLOW_ORIGIN (e.g. `^https://.*$` for testing) — adjust for prod

## 3) JWT keys
```bash
php bin/console lexik:jwt:generate-keypair
```

## 4) Copy configs from this folder
- `config/routes.yaml`
- `config/packages/security.yaml`
- Add `src/Controller/*`, `src/Entity/*`, `src/EventSubscriber/LwwSubscriber.php`

## 5) Migrations & schema
- Put SQL from `../sql/schema.sql` into Doctrine migrations (or create Entities and run `php bin/console make:migration`)
- Add auth extras from `../sql/auth_extras.sql` (tables: `user_providers`, `refresh_tokens`).

## 6) Run
```bash
symfony server:start -d
```

## 7) Notes
- Use LWW (Last-Write-Wins) via `updatedAt` millis. See `src/EventSubscriber/LwwSubscriber.php` for a sketch.
- Keep the API contracts in `../openapi/openapi.yaml` as the single source of truth.
- Make controllers stateless; auth via JWT bearer only.
- Add pagination and validation (Symfony Validator) where needed.

## 8) Auth providers (Google, VK, Yandex)

### 8.1 Google ID Token (`POST /auth/google-idtoken`)
- Validate Google ID Token via JWKS (check `iss`, `aud`, `exp`, signature). Alternatively use Google PHP libs.
- Extract `sub` (provider_user_id) and `email` (`email_verified=true`).
- Upsert `users` and link in `user_providers` with `provider='google'`.
- Issue access/refresh via LexikJWT; store refresh hash in `refresh_tokens`.

### 8.2 VK OAuth (`POST /auth/vk`)
- Exchange `code` -> `access_token` on `https://oauth.vk.com/access_token` using server `client_secret`.
- Fetch profile via `users.get` (email may be present in token response) to get stable VK `user_id`.
- Upsert user and link `user_providers` with `provider='vk'` and `provider_user_id` = VK user_id.
- Issue tokens as above. Consider PKCE on client.

### 8.3 Yandex ID (`POST /auth/yandex`)
- Exchange `code` -> `access_token` on `https://oauth.yandex.ru/token` (`grant_type=authorization_code`).
- Fetch profile via `GET https://login.yandex.ru/info` with OAuth/Bearer; take `id` (uid) and `default_email`.
- Optionally validate `id_token` (if present) via Yandex JWKS (OIDC).
- Upsert user and link `user_providers` with `provider='yandex'` and `provider_user_id` = Yandex uid.

### 8.4 Refresh/Logout
- `/auth/refresh` rotates refresh token (device-bound). Store hashed token; revoke old on use.
- `/auth/logout` revokes refresh for the device.

### 8.5 Provider linking policy
- If email matches an existing verified user — link automatically, else return 409 to require explicit linking.

