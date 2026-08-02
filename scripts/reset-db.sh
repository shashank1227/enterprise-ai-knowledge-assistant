#!/usr/bin/env bash
# reset-db.sh — Drop and recreate the local database (destroys all data)
# Usage: ./scripts/reset-db.sh
set -euo pipefail

COMPOSE_FILE="infrastructure/docker/docker-compose.yml"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'

echo -e "${RED}WARNING: This will DELETE all data in the local database.${NC}"
read -r -p "Type 'yes' to continue: " confirm
[ "$confirm" = "yes" ] || { echo "Aborted."; exit 0; }

echo -e "${YELLOW}Stopping backend...${NC}"
docker compose -f "$COMPOSE_FILE" stop backend 2>/dev/null || true

echo -e "${YELLOW}Dropping and recreating database...${NC}"
docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U knowledge_user -c \
  "DROP DATABASE IF EXISTS knowledge_db; CREATE DATABASE knowledge_db;"

echo -e "${YELLOW}Re-enabling extensions...${NC}"
docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U knowledge_user -d knowledge_db -c \
  "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"; CREATE EXTENSION IF NOT EXISTS \"vector\"; CREATE EXTENSION IF NOT EXISTS \"pg_trgm\";"

echo -e "${GREEN}Database reset. Flyway will re-run migrations on next backend start.${NC}"
echo "Restart backend: docker compose -f $COMPOSE_FILE up -d backend"
