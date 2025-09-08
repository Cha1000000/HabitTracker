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

## 6) Run
```bash
symfony server:start -d
```

## 7) Notes
- Use LWW (Last-Write-Wins) via `updatedAt` millis. See `src/EventSubscriber/LwwSubscriber.php` for a sketch.
- Keep the API contracts in `../openapi/openapi.yaml` as the single source of truth.
- Make controllers stateless; auth via JWT bearer only.
- Add pagination and validation (Symfony Validator) where needed.
