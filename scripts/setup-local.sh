#!/usr/bin/env bash
# setup-local.sh — Bootstrap the local development environment
# Usage: ./scripts/setup-local.sh
set -euo pipefail

COMPOSE_FILE="infrastructure/docker/docker-compose.yml"
ENV_FILE="infrastructure/docker/.env"

# ── Colour helpers ─────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Pre-flight checks ──────────────────────────────────────
command -v docker  &>/dev/null || error "Docker is not installed. Install from https://docs.docker.com/get-docker/"
command -v node    &>/dev/null || warn  "Node.js not found — frontend local dev will need it"
command -v mvn     &>/dev/null || warn  "Maven not found — backend local build will need it"

# ── .env setup ────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  info "Creating $ENV_FILE from .env.example"
  cp "infrastructure/docker/.env.example" "$ENV_FILE"
  warn "Edit $ENV_FILE and set OPENAI_API_KEY before starting the stack"
fi

# Check OPENAI_API_KEY is set
# shellcheck source=/dev/null
source "$ENV_FILE" 2>/dev/null || true
if [ -z "${OPENAI_API_KEY:-}" ]; then
  warn "OPENAI_API_KEY is not set in $ENV_FILE — the backend will start but RAG queries will fail"
fi

# ── Start infrastructure (postgres + redis only first) ─────
info "Starting PostgreSQL and Redis..."
docker compose -f "$COMPOSE_FILE" up -d postgres redis

info "Waiting for PostgreSQL to be ready..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres \
  pg_isready -U knowledge_user -d knowledge_db &>/dev/null; do
  sleep 2
done
info "PostgreSQL is ready."

# ── Install frontend dependencies ──────────────────────────
if command -v node &>/dev/null; then
  info "Installing frontend npm dependencies..."
  (cd frontend && npm install)
fi

# ── Summary ────────────────────────────────────────────────
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Local environment ready!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "  Start full stack:   docker compose -f $COMPOSE_FILE up -d"
echo "  Backend only:       cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo "  Frontend only:      cd frontend && npm run dev"
echo ""
echo "  Services once running:"
echo "    Frontend  →  http://localhost:5173"
echo "    Backend   →  http://localhost:8080"
echo "    Swagger   →  http://localhost:8080/swagger-ui.html"
echo "    Grafana   →  http://localhost:3000  (admin / admin)"
echo "    Prometheus→  http://localhost:9090"
echo ""
