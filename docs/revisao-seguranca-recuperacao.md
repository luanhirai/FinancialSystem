# Revisão de segurança antes da publicação

Data: 19/10/2026. Escopo: autenticação, recuperação de senha, autorização dos dados por usuário, integração Olist, proxy HTTP, dependências de produção e implantação Oracle/Vercel.

## Falhas corrigidas

- Produtos e lojas: bloqueado acesso e alteração de registros de outras contas; criação não aceita IDs arbitrários para sobrescrever registros.
- Cadastro: ID recebido é descartado. Senhas são validadas antes do BCrypt. Login retorna falha genérica para conta inexistente e senha incorreta.
- Sessões: tokens vinculados à senha atual, com identificador aleatório; redefinir a senha invalida sessões anteriores. Logout persiste hashes revogados com expiração.
- Recuperação: token vinculado ao e-mail atual, de uso único, com expiração e bloqueio transacional. Envio em fila limitada evita revelar existência da conta por latência do SMTP.
- Abuso: limites por IP para login, cadastro, recuperação, redefinição e webhooks. Cabeçalhos de origem são validados nas operações de escrita.
- Cookies de produção: Secure, HttpOnly e SameSite=Lax.
- Webhooks: exigem segredo compartilhado, inclusive quando a configuração estiver ausente. Vendas e saldo são consultados na API autenticada da Olist; valores recebidos em notificações não são aceitos como fonte confiável.
- OAuth Olist: state imprevisível, armazenado como hash, vinculado à conta, com expiração de 10 minutos e consumo único. Callback e troca de tokens usam HTTPS.
- Relay Vercel: removida exposição pública de códigos e histórico; páginas não refletem campos externos em HTML; respostas não expõem dados de clientes.
- Proxy: bloqueados caminhos capazes de alterar a autoridade do destino, travessia e barras codificadas. JSON obrigatório para corpos, limite de tamanho, timeout e erros genéricos.
- Segredos: credencial Gmail fora do Git, fora do JAR e do contexto Docker. Produção usa .env protegido. Removidos logs de respostas sensíveis.
- TLS SMTP: verificação do nome do servidor habilitada.
- HTTP produção: HSTS, no-referrer, nosniff e bloqueio de enquadramento. Caddy valida a configuração antes de aplicar o deploy.
- Dependências: Next.js 16.4.2, Spring Boot 4.1.1, JJWT 0.13.0, Tomcat 11.0.25, Jackson 3.1.7 e 2.21.7. npm audit e consulta OSV das 97 dependências Java retornaram zero alertas conhecidos.

## Validação

Testes HTTP com H2 isolado, concorrência de redefinição/OAuth, provedor OAuth local, isolamento entre usuários, origem, cookies, revogação persistente, webhooks, proxy e relay. Build de produção do frontend e pacote Java. SMTP real autenticado a partir do computador e da VM Oracle, sem gravar a senha em logs.

Os testes não acessam dados de produção. A publicação executa backup do banco antes de atualizar os containers e preserva os volumes e as chaves criptográficas existentes.

## Operação

Todos os usuários precisarão entrar novamente após a atualização, pois tokens antigos sem vínculo à senha são recusados.
O limite de IP é local a cada processo; replicar o backend exige um limitador compartilhado. A fila de e-mails não é durável: uma interrupção pode exigir nova solicitação de recuperação. A confirmação HTTP indica que a solicitação foi aceita, não confirma entrega na caixa de entrada.
O redirecionamento HTTPS precisa estar autorizado no aplicativo Olist/Tiny. O relay usa o mesmo OLIST_WEBHOOK_SECRET que a Oracle.
Cadastro público permanece habilitado conforme o produto existente.

A revisão reduz os riscos identificados, mas não prova ausência de toda vulnerabilidade. As consultas cobrem avisos conhecidos na data da revisão, não testes completos de infraestrutura ou pentest externo.

Referências: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
https://github.com/jwtk/jjwt
https://api.osv.dev/
