#!/bin/sh
set -e

for db in "$ORDER_DB_NAME" "$INVENTORY_DB_NAME" "$PAYMENT_DB_NAME"; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "CREATE DATABASE \"$db\""
done
