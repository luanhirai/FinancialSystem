package com.luan.FinancialSystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luan.FinancialSystem.entity.Ecommerce;
import com.luan.FinancialSystem.entity.Product;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.EcommerceRepository;
import com.luan.FinancialSystem.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class TinyProductImportService {

    private static final Path CAMINHO_MOCK = Path.of("mock.md");

    private final ProductRepository productRepository;
    private final EcommerceRepository ecommerceRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TinyProductImportService(ProductRepository productRepository,
                                    EcommerceRepository ecommerceRepository,
                                    AuthenticatedUserService authenticatedUserService) {
        this.productRepository = productRepository;
        this.ecommerceRepository = ecommerceRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public List<Product> ImportacaoTiny() {
        return importarProdutos(lerProdutosTiny());
    }

    private List<Product> importarProdutos(TinyProdutosResponse respostaTiny) {
        User user = authenticatedUserService.getLoggedUser();
        List<Product> produtosImportados = new ArrayList<>();

        for (TinyProdutoResponse produtoTiny : respostaTiny.itens()) {
            Ecommerce ecommerce = buscarOuCriarEcommerce(produtoTiny.ecommerce(), user);

            Product product = productRepository
                    .findByIdOlistAndUserId(String.valueOf(produtoTiny.id()), user.getId())
                    .orElseGet(Product::new);

            product.setId_olist(String.valueOf(produtoTiny.id()));
            product.setName(produtoTiny.descricao());
            product.setOriginal_price(produtoTiny.precos().preco().floatValue());
            product.setCost(produtoTiny.precos().precoCusto().floatValue());
            product.setQuantity(produtoTiny.estoque().quantidade());
            product.setEcommerce(ecommerce);

            produtosImportados.add(productRepository.save(product));
        }

        return produtosImportados;
    }

    private Ecommerce buscarOuCriarEcommerce(TinyEcommerceResponse ecommerceTiny, User user) {
        String nome = ecommerceTiny.nome();

        return ecommerceRepository.findByUserId(user.getId()).stream()
                .filter(ecommerce -> ecommerce.getName().equalsIgnoreCase(nome))
                .findFirst()
                .orElseGet(() -> {
                    Ecommerce ecommerce = new Ecommerce();
                    ecommerce.setName(nome);
                    ecommerce.setRate(0F);
                    ecommerce.setFixed_rate(0F);
                    ecommerce.setUser(user);
                    return ecommerceRepository.save(ecommerce);
                });
    }

    private TinyProdutosResponse lerProdutosTiny() {
        try {
            String markdown = Files.readString(CAMINHO_MOCK);
            String json = extrairJsonDoMarkdown(markdown);
            return objectMapper.readValue(json, TinyProdutosResponse.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel ler o mock de produtos do Tiny em mock.md", e);
        }
    }

    private String extrairJsonDoMarkdown(String markdown) {
        int inicioBlocoJson = markdown.indexOf("```json");
        if (inicioBlocoJson < 0) {
            return markdown;
        }

        int inicioJson = markdown.indexOf('\n', inicioBlocoJson);
        int fimJson = markdown.indexOf("```", inicioJson + 1);

        if (inicioJson < 0 || fimJson < 0) {
            throw new IllegalArgumentException("O arquivo mock.md possui um bloco JSON invalido");
        }
        return markdown.substring(inicioJson + 1, fimJson).trim();
    }

    public record TinyProdutosResponse(
            List<TinyProdutoResponse> itens,
            TinyPaginacaoResponse paginacao
    ) {
    }

    public record TinyPaginacaoResponse(
            Integer pagina,
            Integer limite,
            Integer total,
            Integer totalPaginas
    ) {
    }

    public record TinyProdutoResponse(
            Long id,
            String sku,
            String descricao,
            String tipo,
            String situacao,
            String unidade,
            TinyPrecosResponse precos,
            TinyEstoqueResponse estoque,
            TinyEcommerceResponse ecommerce
    ) {
    }

    public record TinyPrecosResponse(
            Double preco,
            Double precoPromocional,
            Double precoCusto,
            Double precoCustoMedio
    ) {
    }

    public record TinyEstoqueResponse(
            Boolean controlar,
            String localizacao,
            Integer quantidade
    ) {
    }

    public record TinyEcommerceResponse(
            Long id,
            String nome
    ) {
    }
}
