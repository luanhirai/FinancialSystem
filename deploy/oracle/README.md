# Oracle: publicacao
Banco novo e vazio no volume financial-production_mysql_data. Nao importa dados locais.
Execute os comandos no Ubuntu a partir desta pasta:
```bash
bash init-env.sh
sudo docker compose --parallel 1 build
sudo docker compose up -d
sudo docker compose ps
sudo docker compose logs --tail=80 backend frontend caddy
```
DNS: registro A financeiro.casalamavievendas.com.br -> IP publico atual da VM.
Permitir TCP 80 e 443 na lista de seguranca da sub-rede OCI e no firewall do host.
Manter SSH 22 acessivel ao administrador. Nao publicar 3306, 8080 ou 3000.
Caddy emite HTTPS automaticamente quando DNS e portas estiverem corretos.
Acessar https://financeiro.casalamavievendas.com.br/register para criar o usuario.
O cadastro publico segue o comportamento atual da aplicacao.
Nao execute docker compose down -v: isso apaga volumes e dados.
Guarde .env com seguranca. Nao regenere APP_ENCRYPTION_SECRET apos salvar tokens.

## Backup
Na pasta deploy/oracle:
```bash
mkdir -p backups
chmod 700 backups
umask 077
set -o pipefail
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces "$MYSQL_DATABASE"' | gzip > "backups/mysql-$(date +%Y%m%d-%H%M%S).sql.gz"
```
Copie backups para fora da VM, junto de uma copia protegida do .env.

## Integracao Olist/Tiny
A publicacao inicial nao altera o webhook existente na Vercel.
Para conecta-lo ao novo backend, configurar BACKEND_URL na Vercel para
https://financeiro.casalamavievendas.com.br/api/backend e OLIST_WEBHOOK_SECRET
com o mesmo valor do .env no servidor. Fazer redeploy da Vercel.
O banco novo exige cadastrar novamente as credenciais e autorizar a Olist.
Validar login, produtos, callback e webhooks apos a publicacao.
