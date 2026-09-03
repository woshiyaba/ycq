# Repository Guidelines

## Project Structure & Module Organization

This is a Java 8, Maven multi-module Spring Cloud marketplace with a WeChat Mini Program. The root `pom.xml` aggregates shared libraries (`common`, `inner-api`) and six applications: `eureka`, `gateway`, `auth-service`, `user-service`, `goods-service`, and `im-service`. Backend modules follow Maven layout: production code is in `src/main/java`, configuration is in `src/main/resources`, and tests are in `src/test/java`. Profile-specific settings use `application-dev.yml` and `application-docker.yml`.

The `wx-front/` directory contains Mini Program pages, services, configuration, and static images. Repository screenshots live in `assets/`; local MySQL, Redis, RabbitMQ, and ngrok resources live in `dev/`.

## Build, Test, and Development Commands

- `mvn clean package` — compile every module, run tests, and create service JARs.
- `mvn -pl goods-service -am test` — test one module plus its reactor dependencies.
- `docker-compose up --build` — build and start infrastructure and all backend services; the gateway is exposed on port 8080.
- `docker-compose down` — stop the local stack.

For frontend work, import `wx-front/` into WeChat Developer Tools and use its Compile command; there is no npm build in this repository.

## Coding Style & Naming Conventions

Keep backend changes Java 8 compatible. Use four-space indentation, lowercase package names, `PascalCase` classes, and `camelCase` methods and fields. Preserve existing layer suffixes such as `Controller`, `Service`, `ServiceImpl`, `Mapper`, `DTO`, and `Vo`. Mini Program JavaScript uses two-space indentation; keep each page's `.js`, `.json`, `.wxml`, and `.wxss` files together. No formatter or linter is configured, so match nearby code and avoid unrelated reformatting.

## Testing Guidelines

Backend tests use JUnit 4 with Spring Boot's test support. Mirror production packages under each module's `src/test/java` and name test classes `*Tests.java` (or `Test*` for focused utilities). Add a targeted test for changed behavior, then run the relevant module command above. No coverage threshold is configured. Document any MySQL, Redis, or RabbitMQ prerequisite required by an integration test.

## Commit & Pull Request Guidelines

History favors short subjects, including `fix:` prefixes and concise Chinese descriptions. Prefer an imperative, scoped form such as `fix: correct Docker MySQL startup` or `docs: clarify local setup`; keep each commit to one concern. Pull requests should explain the change, affected modules, verification commands, configuration or schema impact, and linked issues. Include screenshots for visible `wx-front` changes.

## Security & Configuration

Never commit real WeChat credentials or production keys. Keep local secrets in the ignored `auth-service/src/main/resources/application-secret.yml`, and treat bundled RSA material as development-only.
