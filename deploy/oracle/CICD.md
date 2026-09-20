# GitHub Actions -> Oracle
Workflow: .github/workflows/oracle.yml.
PRs and pushes to main run Maven verify with an isolated MySQL and npm ci/build.
After successful CI, pushes to main deploy if ORACLE_DEPLOY_ENABLED=true.
Manual runs from main are also supported. Deploys are serialized.

## One-time GitHub setup
Repository Settings -> Environments: create production.
Add secrets to this environment:
- ORACLE_HOST: 129.146.114.113
- ORACLE_USER: ubuntu
- ORACLE_SSH_KEY: private SSH key dedicated to deployment (never commit it).
- ORACLE_KNOWN_HOSTS: trusted host-key line from the verified server.
Install the matching public deploy key in /home/ubuntu/.ssh/authorized_keys.
Use restricted SSH key options (restrict) to disable forwarding and PTY.
The ubuntu user needs passwordless sudo for Docker, as in the current setup.

Get the trusted public host key through the already authenticated SSH session:
sudo cat /etc/ssh/ssh_host_ed25519_key.pub
ORACLE_KNOWN_HOSTS format:
129.146.114.113 ssh-ed25519 PUBLIC_KEY_FROM_SERVER
Do not obtain trust by blindly accepting ssh-keyscan output.

Repository Settings -> Secrets and variables -> Actions -> Variables:
ORACLE_DEPLOY_ENABLED=true
Commit and push the intended application files and deployment files to main.
Local uncommitted files are NOT deployed by this workflow.
Never commit .env, private keys, database dumps or credentials.

## Runtime
The existing ~/financial-system/deploy/oracle/.env is preserved.
Each deployment extracts the exact commit into ~/financial-releases/<commit-run-attempt>.
Compose project financial-production preserves the database and Caddy volumes.
Builds run on the ARM Oracle VM, sequentially; the running app stays up during build.
There may be a brief interruption when containers are recreated.
A database dump is saved before applying the deployment.
Health checks verify HTTPS login and the protected backend via the Next.js proxy.
No automatic schema rollback: Hibernate update can change the database.
Review disk space periodically: releases, Docker build cache and backups accumulate.
Current release path: ~/financial-current-release.
This workflow does not deploy webhook_vercel; configure its existing Vercel integration separately.

## Cost
Standard GitHub-hosted runners are free for public repositories.
Private repositories consume the account free allowance; enforce a spending budget
with usage blocking to avoid charges beyond that allowance before enabling this workflow.
No artifacts or Actions caches are uploaded by this workflow.
See https://docs.github.com/en/billing/concepts/product-billing/github-actions
