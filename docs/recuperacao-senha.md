# Recuperação de senha com Gmail

No ambiente do backend, configure:

```dotenv
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=finsysmavie@gmail.com
MAIL_PASSWORD=senha-de-app-do-google
MAIL_FROM=finsysmavie@gmail.com
APP_FRONTEND_URL=http://localhost:3000
```

Use uma senha de app da conta Google com verificação em duas etapas. Não use a senha normal da conta. Contas gerenciadas podem bloquear senhas de app.
Ajuda oficial: https://support.google.com/accounts/answer/185833?hl=pt-BR

Essas variáveis precisam estar disponíveis no processo Java (configuração de execução da IDE, variáveis de ambiente do servidor ou environment/env_file do contêiner). O Spring não carrega automaticamente um arquivo .env.
Em produção, APP_FRONTEND_URL deve ser a URL HTTPS pública do frontend, sem barra final. Não coloque credenciais em NEXT_PUBLIC_* nem no Git.

Reinicie o backend após configurar. O Hibernate cria password_reset_tokens com o ddl-auto=update já existente; ambientes que usam migrações devem criar essa tabela antes de liberar o recurso.

## Como testar a entrega real

1. Cadastre uma conta usando um e-mail que você consegue acessar.
2. Abra /login e clique em "Esqueci minha senha".
3. Informe o e-mail cadastrado e confira a caixa de entrada e o spam.
4. Abra o link recebido e informe a nova senha duas vezes.
5. Faça login com a nova senha; a senha anterior deve ser recusada.
6. Reabra o mesmo link: a alteração deve ser recusada. Links também expiram após 30 minutos.
7. Solicite novo link após 60 segundos: o link anterior deve ser recusado.

O backend usa SMTP real, não simulação. Sem configuração, retorna erro de indisponibilidade. A resposta confirma o recebimento da solicitação; a entrega acontece em uma fila limitada, sem revelar se a conta existe. Falhas SMTP são registradas sem dados sensíveis; solicite novo link se necessário. O conteúdo do e-mail e o token não são registrados em logs. Apenas o hash SHA-256 do token fica no banco; o link usa fragmento para evitar envio do segredo nos acessos HTTP à página.

Resposta para endereço desconhecido é genérica. Solicitações para a mesma conta têm intervalo mínimo de 60 segundos. Configure limitação por IP no proxy de produção para proteger o endpoint público de abuso distribuído.

Referência de envio Spring: https://docs.spring.io/spring-boot/reference/io/email.html

## Configuração local da senha

Para desenvolvimento, application.properties importa opcionalmente por caminho de arquivo
src/main/resources/application-mail-local.properties. Esse arquivo é ignorado pelo Git.
A senha já configurada foi preservada nesse arquivo local; ela não deve ser commitada. O Maven exclui esse arquivo do JAR e o Docker exclui do contexto de build.

Use spring.mail.password=${MAIL_PASSWORD:} no arquivo local para ler a variável de ambiente,
ou configure a senha de app apenas nesse arquivo ignorado. Para produção, prefira MAIL_PASSWORD.
Variáveis de ambiente do Spring, como SPRING_MAIL_PASSWORD, também podem sobrescrever a configuração.

## Testes

A suíte usa H2 apenas em testes de integração HTTP, sem alterar contas do banco real.
GmailSmtpSmokeTests é ignorado por padrão. Para executar a verificação real de SMTP,
defina FINSYS_TEST_SMTP=true e execute ./mvnw -Dtest=GmailSmtpSmokeTests test.
Esse teste autentica no Gmail e envia um único e-mail ao próprio remetente, sem alterar senhas.
O sucesso confirma a aceitação pelo servidor SMTP; confira a caixa de entrada para verificar a entrega.
