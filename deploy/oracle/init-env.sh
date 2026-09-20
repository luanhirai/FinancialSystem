#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -e .env ]; then
  echo "Arquivo .env ja existe. Chaves preservadas."
  exit 0
fi
umask 077
command -v openssl >/dev/null
{
  printf 'APP_DOMAIN=financeiro.casalamavievendas.com.br\n'
  for name in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET APP_ENCRYPTION_SECRET OLIST_WEBHOOK_SECRET; do
    printf '%s=%s\n' "$name" "$(openssl rand -hex 32)"
  done
} > .env
echo ".env criado. Guarde uma copia segura; nao publique este arquivo."
