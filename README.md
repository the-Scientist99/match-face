# Sports League

A web app for organizing football matches, tracking player stats, and running leagues and cups.
Design: `docs/superpowers/specs/2026-10-28-sports-league-app-design.md`. Plans: `docs/plans/`.

## Prerequisites

- Java 25
- Node 24
- MySQL (installed locally; no Docker needed)

## One-time setup

Create the dev and test databases and the `sportsapp` user (local development only):

```bash
mysql -u root -p < docs/dev/mysql-setup.sql
```

## Run

```bash
cd backend && ./mvnw spring-boot:run     # API on http://localhost:8080
cd frontend && npm install && npm run dev  # app on http://localhost:5173 (proxies /api)
```

## Test

```bash
cd backend && ./mvnw test    # uses the sportsapp_test database; tables are wiped before each test
cd frontend && npm test
```

## API types

With the backend running, regenerate the frontend's API types and commit the result:

```bash
cd frontend && npm run gen:api
```
