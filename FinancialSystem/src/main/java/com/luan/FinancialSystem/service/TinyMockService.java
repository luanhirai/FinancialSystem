package com.luan.FinancialSystem.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TinyMockService {

    private static final Integer SITUACAO_ABERTA = 0;
    private static final Integer SITUACAO_APROVADA = 3;
    private static final Integer SITUACAO_CANCELADA = 2;

    private final List<Map<String, Object>> pedidos = new ArrayList<>();
    private final Map<Long, Map<String, Object>> produtos = new LinkedHashMap<>();

    public TinyMockService() {
        carregarProdutosMockados();
        carregarPedidosMockados();
    }

    public Map<String, Object> listarPedidos(LocalDate dataInicial, LocalDate dataFinal) {
        List<Map<String, Object>> itens = pedidos.stream()
                .filter(pedido -> estaNoPeriodo((String) pedido.get("data"), dataInicial, dataFinal))
                .map(this::resumoPedido)
                .toList();

        return respostaPaginada(itens);
    }

    public Map<String, Object> listarPedidosConfirmados(LocalDate data) {
        List<Map<String, Object>> itens = pedidos.stream()
                .filter(pedido -> pedido.get("situacao").equals(SITUACAO_APROVADA))
                .filter(pedido -> estaNoPeriodo((String) pedido.get("data"), data, data))
                .map(this::resumoPedido)
                .toList();

        return respostaPaginada(itens);
    }

    public Map<String, Object> obterPedido(Long idPedido) {
        return pedidos.stream()
                .filter(pedido -> pedido.get("id").equals(idPedido))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Pedido mockado nao encontrado"));
    }

    public Map<String, Object> obterProduto(Long idProduto) {
        return Optional.ofNullable(produtos.get(idProduto))
                .orElseThrow(() -> new RuntimeException("Produto mockado nao encontrado"));
    }

    public Map<String, Object> listarCustosProduto(Long idProduto) {
        Map<String, Object> produto = obterProduto(idProduto);
        Map<String, Object> precos = map(produto.get("precos"));

        Map<String, Object> custo = new LinkedHashMap<>();
        custo.put("data", LocalDate.now().toString());
        custo.put("saldoAtual", map(produto.get("estoque")).get("quantidade"));
        custo.put("saldoAnterior", 0);
        custo.put("precoCusto", precos.get("precoCusto"));
        custo.put("custoMedio", precos.get("precoCustoMedio"));
        custo.put("precoVenda", precos.get("preco"));
        custo.put("impostosRecuperaveis", 0);

        return respostaPaginada(List.of(custo));
    }

    public List<Map<String, Object>> calcularLucroProdutosConfirmados(LocalDate data) {
        List<Map<String, Object>> resultado = new ArrayList<>();

        pedidos.stream()
                .filter(pedido -> pedido.get("situacao").equals(SITUACAO_APROVADA))
                .filter(pedido -> estaNoPeriodo((String) pedido.get("data"), data, data))
                .forEach(pedido -> adicionarProdutosDoPedido(resultado, pedido));

        return resultado;
    }

    private void adicionarProdutosDoPedido(List<Map<String, Object>> resultado, Map<String, Object> pedido) {
        List<Map<String, Object>> itens = list(pedido.get("itens"));
        BigDecimal valorTotalProdutos = decimal(pedido.get("valorTotalProdutos"));

        for (Map<String, Object> item : itens) {
            Map<String, Object> produtoResumo = map(item.get("produto"));
            Map<String, Object> produto = obterProduto(number(produtoResumo.get("id")).longValue());
            Map<String, Object> precos = map(produto.get("precos"));

            int quantidade = number(item.get("quantidade")).intValue();
            BigDecimal valorUnitario = decimal(item.get("valorUnitario"));
            BigDecimal custoProduto = decimal(precos.get("precoCusto"));

            for (int unidade = 1; unidade <= quantidade; unidade++) {
                resultado.add(calcularLucroUnidade(pedido, produto, unidade, valorUnitario, custoProduto, valorTotalProdutos));
            }
        }
    }

    private Map<String, Object> calcularLucroUnidade(
            Map<String, Object> pedido,
            Map<String, Object> produto,
            int unidade,
            BigDecimal valorUnitario,
            BigDecimal custoProduto,
            BigDecimal valorTotalProdutos
    ) {
        Map<String, Object> ecommerce = map(pedido.get("ecommerce"));
        BigDecimal taxaPercentual = decimal(ecommerce.get("taxaPercentual"));
        BigDecimal taxaFixa = decimal(ecommerce.get("taxaFixa"));
        BigDecimal impostoPercentual = decimal(produto.get("impostoPercentual"));
        BigDecimal embalagem = decimal(produto.get("custoEmbalagem"));

        BigDecimal proporcao = valorTotalProdutos.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : valorUnitario.divide(valorTotalProdutos, 6, RoundingMode.HALF_UP);

        BigDecimal descontoRateado = decimal(pedido.get("valorDesconto")).multiply(proporcao);
        BigDecimal freteRateado = decimal(pedido.get("valorFrete")).multiply(proporcao);
        BigDecimal taxaMarketplace = valorUnitario.multiply(taxaPercentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal imposto = valorUnitario.multiply(impostoPercentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal lucroLiquido = valorUnitario
                .subtract(custoProduto)
                .subtract(taxaMarketplace)
                .subtract(taxaFixa)
                .subtract(imposto)
                .subtract(embalagem)
                .subtract(descontoRateado)
                .subtract(freteRateado);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("pedidoId", pedido.get("id"));
        resultado.put("numeroPedido", pedido.get("numeroPedido"));
        resultado.put("produtoId", produto.get("id"));
        resultado.put("sku", produto.get("sku"));
        resultado.put("descricao", produto.get("descricao"));
        resultado.put("unidade", unidade);
        resultado.put("valorBrutoUnitario", moeda(valorUnitario));
        resultado.put("custoProduto", moeda(custoProduto));
        resultado.put("taxaMarketplace", moeda(taxaMarketplace));
        resultado.put("taxaFixa", moeda(taxaFixa));
        resultado.put("imposto", moeda(imposto));
        resultado.put("embalagem", moeda(embalagem));
        resultado.put("descontoRateado", moeda(descontoRateado));
        resultado.put("freteRateado", moeda(freteRateado));
        resultado.put("lucroLiquido", moeda(lucroLiquido));
        return resultado;
    }

    private Map<String, Object> resumoPedido(Map<String, Object> pedido) {
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("id", pedido.get("id"));
        resumo.put("situacao", pedido.get("situacao"));
        resumo.put("numeroPedido", pedido.get("numeroPedido"));
        resumo.put("ecommerce", pedido.get("ecommerce"));
        resumo.put("dataCriacao", pedido.get("data"));
        resumo.put("cliente", pedido.get("cliente"));
        resumo.put("valor", pedido.get("valorTotalPedido").toString());
        resumo.put("origemPedido", pedido.get("origemPedido"));
        return resumo;
    }

    private Map<String, Object> respostaPaginada(List<Map<String, Object>> itens) {
        Map<String, Object> paginacao = new LinkedHashMap<>();
        paginacao.put("limit", 100);
        paginacao.put("offset", 0);
        paginacao.put("total", itens.size());

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("itens", itens);
        resposta.put("paginacao", paginacao);
        return resposta;
    }

    private boolean estaNoPeriodo(String dataPedido, LocalDate dataInicial, LocalDate dataFinal) {
        LocalDate data = LocalDate.parse(dataPedido);
        return !data.isBefore(dataInicial) && !data.isAfter(dataFinal);
    }

    private void carregarProdutosMockados() {
        produtos.put(101L, produto(101L, "CAM-BASIC-P", "Camiseta Basic Preta", 75.00, 32.00, 30.50, 40, 6.00, 2.50));
        produtos.put(202L, produto(202L, "BON-CLASSIC", "Bone Classic", 100.00, 45.00, 43.80, 18, 6.00, 3.00));
        produtos.put(303L, produto(303L, "CAN-ECO-500", "Caneca Eco 500ml", 59.90, 21.00, 20.20, 60, 5.00, 2.00));
    }

    private void carregarPedidosMockados() {
        LocalDate hoje = LocalDate.now();

        pedidos.add(pedido(987654L, 12045, SITUACAO_APROVADA, hoje, 10.00, 30.00,
                item(101L, 2, 75.00),
                item(202L, 1, 100.00)));

        pedidos.add(pedido(987655L, 12046, SITUACAO_ABERTA, hoje, 0.00, 18.00,
                item(303L, 1, 59.90)));

        pedidos.add(pedido(987656L, 12047, SITUACAO_CANCELADA, hoje, 0.00, 0.00,
                item(202L, 1, 100.00)));

        pedidos.add(pedido(987657L, 12048, SITUACAO_APROVADA, hoje.minusDays(1), 5.00, 20.00,
                item(303L, 3, 59.90)));
    }

    private Map<String, Object> produto(Long id, String sku, String descricao, Double preco, Double precoCusto,
                                        Double precoCustoMedio, Integer estoque, Double imposto, Double embalagem) {
        Map<String, Object> produto = new LinkedHashMap<>();
        produto.put("id", id);
        produto.put("sku", sku);
        produto.put("descricao", descricao);
        produto.put("tipo", "S");
        produto.put("situacao", "A");
        produto.put("unidade", "UN");
        produto.put("categoria", Map.of("id", 1, "nome", "Produtos", "caminhoCompleto", "Produtos"));
        produto.put("precos", Map.of("preco", preco, "precoPromocional", preco, "precoCusto", precoCusto, "precoCustoMedio", precoCustoMedio));
        produto.put("estoque", Map.of("controlar", true, "quantidade", estoque));
        produto.put("impostoPercentual", imposto);
        produto.put("custoEmbalagem", embalagem);
        return produto;
    }

    private Map<String, Object> pedido(Long id, Integer numeroPedido, Integer situacao, LocalDate data,
                                       Double valorDesconto, Double valorFrete, Map<String, Object>... itens) {
        BigDecimal totalProdutos = List.of(itens).stream()
                .map(item -> decimal(item.get("valorUnitario")).multiply(decimal(item.get("quantidade"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPedido = totalProdutos
                .subtract(BigDecimal.valueOf(valorDesconto))
                .add(BigDecimal.valueOf(valorFrete));

        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("id", id);
        pedido.put("numeroPedido", numeroPedido);
        pedido.put("idNotaFiscal", null);
        pedido.put("dataFaturamento", null);
        pedido.put("valorTotalProdutos", moeda(totalProdutos));
        pedido.put("valorTotalPedido", moeda(totalPedido));
        pedido.put("cliente", Map.of("id", 1, "nome", "Cliente Teste", "tipoPessoa", "F", "cpfCnpj", "00000000000"));
        pedido.put("ecommerce", ecommerceMockado());
        pedido.put("pagamento", Map.of("condicaoPagamento", "A vista", "parcelas", List.of(Map.of("dias", 0, "data", data.toString(), "valor", moeda(totalPedido)))));
        pedido.put("itens", List.of(itens));
        pedido.put("situacao", situacao);
        pedido.put("data", data.toString());
        pedido.put("valorDesconto", valorDesconto);
        pedido.put("valorFrete", valorFrete);
        pedido.put("valorOutrasDespesas", 0.00);
        pedido.put("origemPedido", 0);
        return pedido;
    }

    private Map<String, Object> item(Long produtoId, Integer quantidade, Double valorUnitario) {
        Map<String, Object> produto = produtos.get(produtoId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("produto", Map.of("id", produtoId, "sku", produto.get("sku"), "descricao", produto.get("descricao"), "tipo", "S"));
        item.put("quantidade", quantidade);
        item.put("valorUnitario", valorUnitario);
        item.put("infoAdicional", null);
        return item;
    }

    private Map<String, Object> ecommerceMockado() {
        Map<String, Object> ecommerce = new LinkedHashMap<>();
        ecommerce.put("id", 55);
        ecommerce.put("nome", "Mercado Livre");
        ecommerce.put("numeroPedidoEcommerce", "MLB-998877");
        ecommerce.put("numeroPedidoCanalVenda", "MLB-998877");
        ecommerce.put("canalVenda", "marketplace");
        ecommerce.put("taxaPercentual", 12.00);
        ecommerce.put("taxaFixa", 5.00);
        return ecommerce;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private Number number(Object value) {
        return (Number) value;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return BigDecimal.valueOf(number(value).doubleValue());
    }

    private BigDecimal moeda(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
