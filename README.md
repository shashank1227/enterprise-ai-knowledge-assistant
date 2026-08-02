# Enterprise AI Knowledge Assistant

Enterprise AI Knowledge Assistant is a full-stack RAG-powered knowledge assistant for enterprise document search, chat, and knowledge management. The application combines a Spring Boot backend, a React + Vite frontend, PostgreSQL, Redis, and optional monitoring services for local development and deployment.

## Overview

This project is designed to help teams:

- upload and index documents
- chat with an AI assistant grounded in uploaded content
- manage authentication and user access
- explore analytics and observability dashboards
- run the stack locally with Docker Compose or directly on the host

## Architecture at a Glance

- Frontend: React 18, TypeScript, Vite, Tailwind CSS, Zustand
- Backend: Java 21, Spring Boot 3.3, Spring Security, JPA, Flyway
- Database: PostgreSQL with pgvector support
- Cache/Session Store: Redis
- AI/RAG layer: LangChain4j with OpenAI-compatible models
- Storage: Local filesystem or S3-compatible storage
- Infrastructure: Docker Compose, Nginx, Prometheus, Grafana

## Repository Structure

- backend/ - Spring Boot API service
  - src/main/java/ - application code
  - src/main/resources/ - configuration, migrations, templates
  - src/test/java/ - backend tests
  - pom.xml - Maven build definition
- frontend/ - React/Vite client application
  - src/ - components, pages, services, state
  - package.json - Node.js dependencies and scripts
  - vite.config.ts - Vite dev server and proxy config
- infrastructure/ - deployment and local dev infrastructure
  - docker/ - Docker Compose stack
  - nginx/ - reverse proxy config
  - monitoring/ - Prometheus and Grafana config
- docs/ - architecture, database, API documentation
- scripts/ - utility scripts for setup and database reset

## Prerequisites

Before installing the project, make sure the following are available:

- Java 21+
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker Desktop (optional, for the containerized stack)
- PostgreSQL 16+ and Redis 7+ if running services manually
- An OpenAI API key for AI features

## Environment Variables

The application uses environment variables for configuration. The most important ones are:

### Backend

- OPENAI_API_KEY - required for AI/chat features
- SPRING_PROFILES_ACTIVE - defaults to local
- DATABASE_URL - PostgreSQL connection string
- DATABASE_USERNAME - PostgreSQL username
- DATABASE_PASSWORD - PostgreSQL password
- REDIS_HOST / REDIS_PORT - Redis host and port
- JWT_SECRET - signing key for JWT tokens
- STORAGE_TYPE - local or s3
- SERVER_PORT - default 8080

### Frontend

- VITE_API_URL - API base URL for the frontend
  - default: /api/v1 when running through the Vite proxy

## Local Development Setup

### Option 1: Docker Compose (recommended)

This is the easiest way to run the full stack locally.

1. Copy the example environment file if one exists in the repository or create a local environment file:

   ```bash
   cp .env.example .env
   ```

   If no .env.example exists, create .env manually and add at least:

   ```bash
   OPENAI_API_KEY=your_openai_api_key
   JWT_SECRET=change-this-in-production-min-256-bits-long
   ```

2. Start the infrastructure services:

   ```bash
   docker compose -f infrastructure/docker/docker-compose.yml up -d postgres redis
   ```

3. Start the full stack:

   ```bash
   docker compose -f infrastructure/docker/docker-compose.yml up -d
   ```

4. Check container logs if needed:

   ```bash
   docker compose -f infrastructure/docker/docker-compose.yml logs -f backend frontend
   ```

5. Open the app:
   - Frontend: http://localhost:5173
   - Backend API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui/index.html
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000

### Option 2: Run Backend and Frontend Manually

#### Start PostgreSQL and Redis

If you are not using Docker, make sure PostgreSQL and Redis are running locally.

Example using Docker for only the database dependencies:

```bash
docker run --name ka_postgres -e POSTGRES_DB=knowledge_db -e POSTGRES_USER=knowledge_user -e POSTGRES_PASSWORD=knowledge_pass -p 5432:5432 -d pgvector/pgvector:pg16
docker run --name ka_redis -p 6379:6379 -d redis:7.4-alpine
```

#### Start the Backend

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
mvn spring-boot:run
```

The backend will start on port 8080.

#### Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend will start on port 5173 and proxy API requests to the backend.

## Build and Verification

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
npm run build
npm run type-check
```

## Useful Commands

### Docker Compose

```bash
# Start all services
docker compose -f infrastructure/docker/docker-compose.yml up -d

# Stop all services
docker compose -f infrastructure/docker/docker-compose.yml down

# View logs
docker compose -f infrastructure/docker/docker-compose.yml logs -f
```

### Database Reset

```bash
./scripts/reset-db.sh
```

### Local Setup Script

```bash
./scripts/setup-local.sh
```

## API Documentation

Once the backend is running, OpenAPI documentation is available at:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Notes

- The local profile uses a local file upload directory and disables rate limiting for easier development.
- For production deployments, configure secure secrets, proper networking, and cloud storage.
- If you are using the Docker Compose stack, ensure the OpenAI API key is present before starting the backend service.

## Troubleshooting

### Backend fails to start

- Check that PostgreSQL and Redis are running.
- Make sure the required environment variables are set.
- Verify that Java 21 is installed.

### Frontend cannot reach the backend

- Confirm the backend is running on port 8080.
- Verify the Vite proxy configuration in frontend/vite.config.ts.
- Check that VITE_API_URL is set correctly.

### Port conflicts

If port 8080 or 5173 is already in use, stop the conflicting process or change the port configuration.
