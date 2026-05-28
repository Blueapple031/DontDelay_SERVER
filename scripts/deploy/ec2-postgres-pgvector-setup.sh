#!/usr/bin/env bash
# dontdelay-DB EC2 최초 1회: PostgreSQL 16 + pgvector (Ubuntu 22.04)
# Usage (on DB EC2):
#   sudo bash ec2-postgres-pgvector-setup.sh [DB_NAME] [DB_USER]
# Example:
#   sudo bash ec2-postgres-pgvector-setup.sh dontdelay dontdelay_app
set -euo pipefail

DB_NAME="${1:-dontdelay}"
DB_USER="${2:-dontdelay_app}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash $0"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y curl ca-certificates gnupg lsb-release

# PostgreSQL 16 + pgvector (PGDG)
install -d /usr/share/postgresql-common/pgdg
curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
  | gpg --dearmor -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg] \
https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
  > /etc/apt/sources.list.d/pgdg.list

apt-get update
apt-get install -y postgresql-16 postgresql-16-pgvector

# Listen on all interfaces (restrict access with security group + pg_hba)
PG_CONF="/etc/postgresql/16/main/postgresql.conf"
PG_HBA="/etc/postgresql/16/main/pg_hba.conf"

sed -i "s/^#*listen_addresses.*/listen_addresses = '*'/" "$PG_CONF"
sed -i "s/^#*max_connections.*/max_connections = 100/" "$PG_CONF"

# Password auth for app user from private network (adjust CIDR to your VPC/subnet)
if ! grep -q "dontdelay-app-ipv4" "$PG_HBA"; then
  cat >> "$PG_HBA" <<'EOF'

# dontdelay Spring app (replace 10.0.0.0/16 with your VPC CIDR or app EC2 /32)
# hostssl  dontdelay  dontdelay_app  10.0.0.0/16  scram-sha-256
host     dontdelay  dontdelay_app  10.0.0.0/16  scram-sha-256
EOF
fi

systemctl enable postgresql
systemctl restart postgresql

# Role + database (prompt for password if not set)
if [ -z "${DB_PASSWORD:-}" ]; then
  read -r -s -p "Password for PostgreSQL user '$DB_USER': " DB_PASSWORD
  echo
fi

escape_sql_literal() {
  printf "%s" "$1" | sed "s/'/''/g"
}
PASS_ESC="$(escape_sql_literal "$DB_PASSWORD")"

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1; then
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c \
    "CREATE ROLE \"${DB_USER}\" LOGIN PASSWORD '${PASS_ESC}';"
else
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c \
    "ALTER ROLE \"${DB_USER}\" WITH PASSWORD '${PASS_ESC}';"
fi

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c \
    "CREATE DATABASE \"${DB_NAME}\" OWNER \"${DB_USER}\";"
fi

sudo -u postgres psql -v ON_ERROR_STOP=1 -c \
  "GRANT ALL PRIVILEGES ON DATABASE \"${DB_NAME}\" TO \"${DB_USER}\";"

sudo -u postgres psql -d "$DB_NAME" -v ON_ERROR_STOP=1 <<SQL
CREATE EXTENSION IF NOT EXISTS vector;
GRANT ALL ON SCHEMA public TO ${DB_USER};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${DB_USER};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${DB_USER};
SQL

echo ""
echo "=== PostgreSQL + pgvector ready ==="
echo "  Database : ${DB_NAME}"
echo "  User     : ${DB_USER}"
echo "  Version  : $(sudo -u postgres psql -tAc 'SELECT version();' | head -1)"
echo "  pgvector : $(sudo -u postgres psql -d "$DB_NAME" -tAc "SELECT extversion FROM pg_extension WHERE extname='vector';")"
echo ""
echo "Next:"
echo "  1) Security group: inbound TCP 5432 only from app EC2 (SG or private IP)"
echo "  2) Edit ${PG_HBA} — replace 10.0.0.0/16 with your real CIDR"
echo "  3) On app EC2 set SPRING_DATASOURCE_URL=jdbc:postgresql://<DB_PRIVATE_IP>:5432/${DB_NAME}"
echo "  See docs/DATABASE_EC2.md"
