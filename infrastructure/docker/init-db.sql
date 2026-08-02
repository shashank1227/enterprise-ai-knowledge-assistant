-- init-db.sql
-- Runs once when the PostgreSQL container is first created.
-- Enables extensions needed by the application.
-- (Flyway migrations handle the actual schema.)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
