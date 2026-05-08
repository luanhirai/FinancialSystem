package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TinyProductImportService {

    private final ProductRepository productRepository;
    private final EcommerceRepository ecommerceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final JdbcTemplate jdbcTemplate;

    public TinyProductImportService(ProductRepository productRepository,
                                    EcommerceRepository ecommerceRepository,
                                    AuthenticatedUserService authenticatedUserService,
                                    JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.ecommerceRepository = ecommerceRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<Product> importarProdutosMockadosDoTiny() {
        User user = authenticatedUserService.getLoggedUser();
        List<Product> produtosImportados = new ArrayList<>();

        for (Map<String, Object> produtoTiny : listarProdutosTinyMockados()) {
            Map<String, Object> ecommerceTiny = map(produtoTiny.get("ecommerce"));
            Ecommerce ecommerce = buscarOuCriarEcommerce(ecommerceTiny, user);

            Product product = productRepository
                    .findByIdOlist(String.valueOf(produtoTiny.get("id")))
                    .orElseGet(Product::new);

            Map<String, Object> precos = map(produtoTiny.get("precos"));
            Map<String, Object> estoque = map(produtoTiny.get("estoque"));

            product.setId_olist(String.valueOf(produtoTiny.get("id")));
            product.setName((String) produtoTiny.get("descricao"));
            product.setOriginal_price(floatValue(precos.get("preco")));
            product.setCost(floatValue(precos.get("precoCusto")));
            product.setQuantity(intValue(estoque.get("quantidade")));
            product.setEcommerce(ecommerce);

            produtosImportados.add(productRepository.save(product));
        }

        return produtosImportados;
    }

    private Ecommerce buscarOuCriarEcommerce(Map<String, Object> ecommerceTiny, User user) {
        Long tinyId = longValue(ecommerceTiny.get("id"));

        return ecommerceRepository.findById(tinyId)
                .orElseGet(() -> {
                    inserirEcommerceComIdTiny(tinyId, (String) ecommerceTiny.get("nome"), user);
                    return ecommerceRepository.findById(tinyId)
                            .orElseThrow(() -> new RuntimeException("Erro ao criar ecommerce importado do Tiny"));
                });
    }

    private void inserirEcommerceComIdTiny(Long tinyId, String nome, User user) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ecommerce (id, name, rate, fixed_rate, user_id) VALUES (?, ?, ?, ?, ?)",
                    tinyId,
                    nome,
                    0F,
                    0F,
                    user.getId()
            );
        } catch (DuplicateKeyException ignored) {
            // Outro request pode ter criado o mesmo ecommerce entre o findById e o insert.
        }
    }

    private List<Map<String, Object>> listarProdutosTinyMockados() {
        List<Map<String, Object>> produtos = new ArrayList<>(List.of(
                produtoTiny(101L, "CAM-BASIC-P", "Camiseta Basic Preta", 75.00, 32.00, 40, 55L, "Mercado Livre"),
                produtoTiny(202L, "BON-CLASSIC", "Bone Classic", 100.00, 45.00, 18, 55L, "Mercado Livre"),
                produtoTiny(303L, "CAN-ECO-500", "Caneca Eco 500ml", 59.90, 21.00, 60, 77L, "Shopee"),
                produtoTiny(304L, "MOCH-URBAN-20L", "Mochila Urban 20L", 189.90, 88.40, 25, 88L, "Amazon"),
                produtoTiny(305L, "GARRAFA-INOX-750", "Garrafa Inox 750ml", 79.90, 31.70, 80, 99L, "Site Proprio"),
                produtoTiny(306L, "MOUSE-WL-ERG", "Mouse Wireless Ergonomico", 129.90, 58.00, 35, 88L, "Amazon"),
                produtoTiny(307L, "TECLADO-MEC-BLUE", "Teclado Mecanico Switch Blue", 249.90, 122.50, 14, 55L, "Mercado Livre"),
                produtoTiny(308L, "SUP-NOTE-ALU", "Suporte Notebook Aluminio", 99.90, 42.80, 46, 77L, "Shopee"),
                produtoTiny(309L, "FONE-BT-BASIC", "Fone Bluetooth Basic", 149.90, 64.20, 55, 55L, "Mercado Livre"),
                produtoTiny(310L, "LUMINARIA-LED-MESA", "Luminaria LED de Mesa", 119.90, 49.30, 33, 99L, "Site Proprio")
        ));

        String[] categorias = {
                "Camiseta", "Calca", "Bone", "Mochila", "Carteira", "Relogio",
                "Caneca", "Garrafa", "Mousepad", "Organizador", "Cabo USB-C",
                "Carregador", "Hub USB", "Fone", "Caixa de Som", "Luminaria",
                "Suporte", "Caderno", "Planner", "Necessaire"
        };

        String[] modelos = {
                "Essential", "Prime", "Urban", "Classic", "Pro", "Mini",
                "Max", "Eco", "Flex", "Studio", "Travel", "Office"
        };

        Long[] ecommerceIds = {55L, 77L, 88L, 99L};
        String[] ecommerceNomes = {"Mercado Livre", "Shopee", "Amazon", "Site Proprio"};

        for (int i = 0; i < 120; i++) {
            String categoria = categorias[i % categorias.length];
            String modelo = modelos[i % modelos.length];
            int variacao = (i / modelos.length) + 1;
            Long ecommerceId = ecommerceIds[i % ecommerceIds.length];
            String ecommerceNome = ecommerceNomes[i % ecommerceNomes.length];

            double preco = 29.90 + ((i % 17) * 11.35) + (variacao * 2.10);
            double custo = preco * (0.38 + ((i % 5) * 0.03));
            int quantidade = 5 + ((i * 7) % 96);

            produtos.add(produtoTiny(
                    1000L + i,
                    sku(categoria, modelo, variacao),
                    categoria + " " + modelo + " " + variacao,
                    arredondar(preco),
                    arredondar(custo),
                    quantidade,
                    ecommerceId,
                    ecommerceNome
            ));
        }

        return produtos;
    }

    private String sku(String categoria, String modelo, int variacao) {
        String categoriaSku = categoria
                .toUpperCase()
                .replace(" ", "-")
                .replace("Ã", "A")
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("Ç", "C");

        return categoriaSku + "-" + modelo.toUpperCase() + "-" + String.format("%03d", variacao);
    }

    private Double arredondar(Double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private Map<String, Object> produtoTiny(Long id, String sku, String descricao, Double preco,
                                            Double precoCusto, Integer quantidade, Long ecommerceId,
                                            String ecommerceNome) {
        Map<String, Object> produto = new LinkedHashMap<>();
        produto.put("id", id);
        produto.put("sku", sku);
        produto.put("descricao", descricao);
        produto.put("tipo", "S");
        produto.put("situacao", "A");
        produto.put("unidade", "UN");
        produto.put("precos", Map.of(
                "preco", preco,
                "precoPromocional", preco,
                "precoCusto", precoCusto,
                "precoCustoMedio", precoCusto
        ));
        produto.put("estoque", Map.of(
                "controlar", true,
                "localizacao", "A1",
                "quantidade", quantidade
        ));
        produto.put("ecommerce", Map.of(
                "id", ecommerceId,
                "nome", ecommerceNome
        ));
        return produto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private Long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private Float floatValue(Object value) {
        return ((Number) value).floatValue();
    }

    private Integer intValue(Object value) {
        return ((Number) value).intValue();
    }
}
