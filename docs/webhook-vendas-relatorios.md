# Vendas e relat?rios
Ative Notifica??es de vendas no aplicativo Webhooks da Olist e configure a URL p?blica do receptor Vercel j? existente: https://webhook.casalamavievendas.com.br/ .
O receptor encaminha inclusao_pedido e atualizacao_pedido para POST /olist/vendas/webhook do backend. O clientId deve corresponder ao aplicativo cadastrado no usu?rio, com OAuth conectado e permiss?o de leitura de pedidos.
O backend consulta GET /pedidos/{id} da API v3 usando o token do usu?rio e salva a resposta completa e o payload original. HTTP 200 ocorre depois da grava??o; falhas de consulta retornam erro permitindo reenvio da Olist. Pedidos repetidos atualizam a mesma venda pela chave usu?rio + ID Olist.
A p?gina /reports lista as vendas do usu?rio, atualiza a cada 15 segundos e permite consultar todos os campos e recalcular.
Valor real = total do pedido - frete cobrado - custo dos produtos - taxa percentual do ecommerce sobre total sem frete - taxa fixa por unidade - imposto cadastrado sobre total sem frete. Descontos j? fazem parte do total. S?o valores calculados com as configura??es locais, n?o concilia??o do repasse efetivo do marketplace. Custos e taxas ausentes deixam o c?lculo pendente, sem estimar custos. Ap?s cadastr?-los, use Recalcular.
Cancelados n?o entram no resumo. Nenhuma chamada lan?a estoque ou contas automaticamente.
As colunas novas s?o criadas pela configura??o existente spring.jpa.hibernate.ddl-auto=update. Fa?a deploy do backend e frontend; este trabalho n?o altera a conta Olist nem publica o projeto.
