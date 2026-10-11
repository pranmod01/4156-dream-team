#!/usr/bin/env bash
# One-time local database setup for resource-allocator.
# Creates the app's Postgres user and database if they don't already exist.
# Run from the repo root: ./scripts/setup-db.sh
# If your Postgres superuser isn't your OS username (e.g. Windows/EDB installs):
#   PGUSER=postgres ./scripts/setup-db.sh
set -euo pipefail

DB_NAME="resource_allocator_db"
DB_USER="resource_allocator"
DB_PASSWORD="dev_password"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found. Install PostgreSQL 17 first (see README)." >&2
  exit 1
fi

if ! pg_isready -q -h localhost -p 5432; then
  echo "Postgres is not running on localhost:5432. Start it first, e.g.:" >&2
  echo "  brew services start postgresql@17" >&2
  exit 1
fi

psql -d postgres -v ON_ERROR_STOP=1 -q <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';
    RAISE NOTICE 'Created user ${DB_USER}';
  ELSE
    RAISE NOTICE 'User ${DB_USER} already exists, skipping';
  END IF;
END
\$\$;
SQL

if psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1; then
  echo "Database ${DB_NAME} already exists, skipping"
else
  psql -d postgres -v ON_ERROR_STOP=1 -q -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER}"
  echo "Created database ${DB_NAME}"
fi

echo "Done. Start the app with ./mvnw spring-boot:run to create the tables."
