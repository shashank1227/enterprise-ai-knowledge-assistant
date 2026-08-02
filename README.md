# Enterprise AI Knowledge Assistant

RAG-powered knowledge assistant for enterprise document search, grounded chat, and knowledge management. Upload documents, ask questions with citations, and manage access through a Spring Boot API and React client.

## Features

- **Document ingestion** — upload, parse, chunk, embed, and index files into PostgreSQL + pgvector
- **RAG chat** — hybrid retrieval (vector + keyword) with source citations and optional SSE streaming
- **Auth** — JWT access/refresh tokens, signup/login, role-based access (`USER`, `VIEWER`, `ADMIN`)
- **Conversations** — multi-turn history, pinning, and message feedback
- **Storage** — local filesystem or S3-compatible object storage
- **Observability** — Spring Actuator, Prometheus metrics, Grafana dashboards

## Tech Stack

| Layer | Technologies |
| --- | --- |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Zustand |
| Backend | Java 21, Spring Boot 3.3, Spring Security, JPA, Flyway |
| AI / RAG | LangChain4j, OpenAI-compatible chat & embedding models |
| Data | PostgreSQL 16 + pgvector, Redis 7 |
| Infra | Docker Compose, Nginx, Prometheus, Grafana |

## Repository Layout

```
backend/                 Spring Boot API
frontend/                React + Vite client
infrastructure/
  docker/                Docker Compose stack + .env.example
  nginx/                 Reverse proxy
  monitoring/            Prometheus & Grafana
docs/
  api/                   OpenAPI contracts
  database/              Schema reference
scripts/                 Local setup and DB reset helpers
```

## Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 22+ and npm 10+
- Docker Desktop (recommended for Postgres, Redis, and the full stack)
- An OpenAI API key for chat and embeddings

## Quick Start

### 1. Bootstrap local infrastructure

```bash
./scripts/setup-local.sh
```

This copies `infrastructure/docker/.env.example` → `infrastructure/docker/.env`, starts PostgreSQL and Redis, and installs frontend dependencies.

Edit `infrastructure/docker/.env` and set at least:

```bash
OPENAI_API_KEY=sk-...
JWT_SECRET=change-this-in-production-must-be-at-least-256-bits-long
```

### 2. Run the full stack with Docker Compose

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d
```

### 3. Open the app

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Nginx (proxy) | http://localhost:80 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (default `admin` / `admin`) |

## Manual Development Setup

Use this when you want hot-reload on the host while Postgres/Redis run in Docker.

### Start dependencies

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres redis
```

Or with standalone containers:

```bash
docker run --name ka_postgres \
  -e POSTGRES_DB=knowledge_db \
  -e POSTGRES_USER=knowledge_user \
  -e POSTGRES_PASSWORD=knowledge_pass \
  -p 5432:5432 -d pgvector/pgvector:pg16

docker run --name ka_redis -p 6379:6379 -d redis:7.4-alpine
```

### Backend

```bash
cd backend
export SPRING_PROFILES_ACTIVE=local
export DATABASE_URL=jdbc:postgresql://localhost:5432/knowledge_db
export DATABASE_USERNAME=knowledge_user
export DATABASE_PASSWORD=knowledge_pass
export REDIS_HOST=localhost
export REDIS_PORT=6379
export OPENAI_API_KEY=your_openai_api_key
export JWT_SECRET=local-dev-secret-at-least-256-bits-long-do-not-use-in-prod
export STORAGE_TYPE=local
mvn spring-boot:run
```

API listens on port `8080`. Flyway applies migrations on startup.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Dev server listens on port `5173` and proxies `/api` to the backend.

## Environment Variables

### Backend (required / commonly used)

| Variable | Description |
| --- | --- |
| `OPENAI_API_KEY` | OpenAI (or compatible) API key |
| `JWT_SECRET` | JWT signing secret (≥ 256 bits) |
| `SPRING_PROFILES_ACTIVE` | Profile (`local` by default) |
| `DATABASE_URL` | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection |
| `STORAGE_TYPE` | `local` or `s3` |
| `SERVER_PORT` | Defaults to `8080` |

Optional AI / RAG knobs include `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`, `RAG_CHUNK_SIZE`, `RAG_TOP_K`, and `RAG_SEARCH_MODE` (`HYBRID` \| `VECTOR` \| `KEYWORD`).

When `STORAGE_TYPE=s3`, also set `AWS_REGION`, `S3_BUCKET`, and AWS credentials.

### Frontend

| Variable | Description |
| --- | --- |
| `VITE_API_URL` | API base path (default `/api/v1` via Vite proxy) |

See `infrastructure/docker/.env.example` for the Compose-oriented template.

## Build & Test

### Backend

```bash
cd backend
mvn test
mvn package
```

### Frontend

```bash
cd frontend
npm install
npm run type-check
npm run build
```

## Useful Commands

```bash
# Full stack
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml logs -f backend frontend
docker compose -f infrastructure/docker/docker-compose.yml down

# Reset database volume / schema helpers
./scripts/reset-db.sh
```

## API Documentation

With the backend running:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Contract reference: [`docs/api/api-contracts.yaml`](docs/api/api-contracts.yaml)
- Database schema reference: [`docs/database/schema.sql`](docs/database/schema.sql)

Main API prefixes:

- `/api/v1/auth` — signup, login, refresh, logout
- `/api/v1/documents` — upload, list, delete, re-index
- `/api/v1/chat` — conversations, RAG answers, feedback, streaming

## Notes

- The `local` profile uses local file storage and is intended for development.
- Set real secrets, CORS origins, and storage settings before any production deploy.
- Without `OPENAI_API_KEY`, the stack can start but RAG chat and embedding will fail.
- CI runs backend tests (with pgvector + Redis services) and frontend type-check/build via GitHub Actions.

## Troubleshooting

**Backend will not start**
- Confirm PostgreSQL and Redis are healthy.
- Verify Java 21+ and required env vars (`OPENAI_API_KEY`, `JWT_SECRET`, DB settings).

**Frontend cannot reach the API**
- Ensure the backend is on port `8080`.
- Check the Vite proxy in `frontend/vite.config.ts` and `VITE_API_URL`.

**Port already in use**
- Stop the conflicting process, or change `SERVER_PORT` / the Vite port.

**Compose env file missing**
- Copy `infrastructure/docker/.env.example` to `infrastructure/docker/.env` (or run `./scripts/setup-local.sh`).
