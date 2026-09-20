#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Same Compose project name preserves existing database and TLS volumes.
exec 9>"$HOME/.financial-deploy.lock"
flock -n 9 || { echo "Another deploy is running"; exit 1; }
shared_env="$HOME/financial-system/deploy/oracle/.env"
test -s "$shared_env" || { echo "Missing production .env"; exit 1; }
if [ "$(pwd)/.env" != "$shared_env" ]; then
  ln -sfn "$shared_env" .env
fi
sudo docker compose config --quiet
# Fail before touching running containers if the new code does not build.
sudo docker compose --parallel 1 build
# Save database before application changes. Keep backups outside release folders.
backup_dir="$HOME/financial-backups"
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
umask 077
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces "$MYSQL_DATABASE"' | gzip > "$backup_dir/pre-deploy-$(date +%Y%m%d-%H%M%S).sql.gz"
sudo docker compose up -d --no-build
domain=$(sed -n 's/^APP_DOMAIN=//p' .env | tr -d '\r')
[[ "$domain" =~ ^[a-zA-Z0-9.-]+$ ]]
for attempt in $(seq 1 36); do
  if curl --fail --silent --show-error --max-time 5 --resolve "$domain:443:127.0.0.1" "https://$domain/login" >/dev/null; then
    # Anonymous /auth/me must reach the backend and return 401 or 403.
    status=$(curl --silent --max-time 5 --resolve "$domain:443:127.0.0.1" -o /dev/null -w '%{http_code}' "https://$domain/api/backend/auth/me" || true)
    if [ "$status" = 401 ] || [ "$status" = 403 ]; then
      printf '%s\n' "$(pwd)" > "$HOME/financial-current-release"
      echo "Deployment verified: https://$domain"
      exit 0
    fi
  fi
  sleep 5
done
sudo docker compose ps
echo "Deployment health check failed. Inspect containers; automatic database rollback is not performed."
exit 1
