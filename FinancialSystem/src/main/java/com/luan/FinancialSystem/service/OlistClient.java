package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.service.dto.OlistPedidoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistPedidosResponse;
import com.luan.FinancialSystem.service.dto.OlistProdutoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistProdutosResponse;
import com.luan.FinancialSystem.service.dto.OlistSaldoEstoque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
public class OlistClient {
    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 60_000L;
    private static final int PEDIDOS_PAGE_LIMIT = 100;
    private static final int INITIAL_REQUESTS_PER_MINUTE = 100;
    private static final int REDUCED_REQUESTS_PER_MINUTE = 90;

    private final Object rateLimitLock = new Object();
    private volatile int requestsPerMinute = INITIAL_REQUESTS_PER_MINUTE;
    private long nextRequestAtMs = 0L;

    private final RestClient restClient;
    private final OlistAuthService olistAuthService;

    public OlistClient(
            @Value("${olist.api.base-url}") String baseUrl,
            OlistAuthService olistAuthService
    ) {
        this.olistAuthService = olistAuthService;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public OlistPedidosResponse listarPedidos(LocalDate dataInicial, LocalDate dataFinal) {
        System.out.println("[BACKEND OLIST API] Listando TODOS os pedidos do periodo na API Olist/Tiny usando token salvo no banco.");
        List<com.luan.FinancialSystem.service.dto.OlistPedidoResumo> todosPedidos = new ArrayList<>();
        int offset = 0;
        Integer total = null;

        while (total == null || todosPedidos.size() < total) {
            System.out.println("[BACKEND OLIST API] Buscando pagina de pedidos. limit=" + PEDIDOS_PAGE_LIMIT + ", offset=" + offset);
            OlistPedidosResponse pagina = listarPedidosPagina(dataInicial, dataFinal, PEDIDOS_PAGE_LIMIT, offset);

            if (pagina == null || pagina.itens() == null || pagina.itens().isEmpty()) {
                System.out.println("[BACKEND OLIST API] API nao retornou mais pedidos. Encerrando paginacao.");
                break;
            }

            todosPedidos.addAll(pagina.itens());

            if (pagina.paginacao() != null && pagina.paginacao().total() != null) {
                total = pagina.paginacao().total();
            }

            int pageLimit = pagina.paginacao() != null && pagina.paginacao().limit() != null && pagina.paginacao().limit() > 0
                    ? pagina.paginacao().limit()
                    : PEDIDOS_PAGE_LIMIT;

            offset += pageLimit;
            System.out.println("[BACKEND OLIST API] Pedidos acumulados: " + todosPedidos.size() + (total != null ? " de " + total : ""));

            if (pagina.itens().size() < pageLimit) {
                System.out.println("[BACKEND OLIST API] Ultima pagina detectada porque retornou menos itens que o limit.");
                break;
            }
        }

        int totalFinal = total != null ? total : todosPedidos.size();
        System.out.println("[BACKEND OLIST API] Fim da paginacao. Total devolvido ao frontend: " + todosPedidos.size());
        return new OlistPedidosResponse(
                todosPedidos,
                new com.luan.FinancialSystem.service.dto.OlistPaginacao(PEDIDOS_PAGE_LIMIT, 0, totalFinal)
        );
    }

    private OlistPedidosResponse listarPedidosPagina(LocalDate dataInicial, LocalDate dataFinal, int limit, int offset) {
        return executeWithRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/pedidos")
                        .queryParam("dataInicial", dataInicial)
                        .queryParam("dataFinal", dataFinal)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(OlistPedidosResponse.class));
    }

    public OlistPedidoDetalhe obterPedido(Long idPedido) {
        System.out.println("[BACKEND OLIST API] Buscando pedido " + idPedido + " na API Olist/Tiny usando token salvo no banco.");
        return executeWithRetry(() -> restClient.get()
                .uri("/pedidos/{idPedido}", idPedido)
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(OlistPedidoDetalhe.class));
    }

    public OlistPedidosResponse listarPedidos(Long userId, LocalDate dataInicial, LocalDate dataFinal) {
        List<com.luan.FinancialSystem.service.dto.OlistPedidoResumo> pedidos = new ArrayList<>();
        int offset = 0;
        Integer total = null;
        while (total == null || pedidos.size() < total) {
            int currentOffset = offset;
            OlistPedidosResponse pagina = executeWithRetryForUser(() -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/pedidos")
                            .queryParam("dataInicial", dataInicial)
                            .queryParam("dataFinal", dataFinal)
                            .queryParam("limit", PEDIDOS_PAGE_LIMIT)
                            .queryParam("offset", currentOffset).build())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .retrieve().body(OlistPedidosResponse.class), userId);
            if (pagina == null || pagina.itens() == null || pagina.itens().isEmpty()) break;
            pedidos.addAll(pagina.itens());
            if (pagina.paginacao() != null && pagina.paginacao().total() != null) total = pagina.paginacao().total();
            int limit = pagina.paginacao() != null && pagina.paginacao().limit() != null
                    ? pagina.paginacao().limit() : PEDIDOS_PAGE_LIMIT;
            offset += limit;
            if (pagina.itens().size() < limit) break;
        }
        return new OlistPedidosResponse(pedidos,
                new com.luan.FinancialSystem.service.dto.OlistPaginacao(PEDIDOS_PAGE_LIMIT, 0,
                        total != null ? total : pedidos.size()));
    }

    public OlistPedidoDetalhe obterPedido(Long idPedido, Long userId) {
        return executeWithRetryForUser(() -> restClient.get()
                .uri("/pedidos/{idPedido}", idPedido)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                .retrieve().body(OlistPedidoDetalhe.class), userId);
    }

    public OlistProdutoDetalhe obterProduto(Long idProduto) {
        System.out.println("[BACKEND OLIST API] Buscando produto " + idProduto + " na API Olist/Tiny usando token salvo no banco.");
        return executeWithRetry(() -> restClient.get()
                .uri("/produtos/{idProduto}", idProduto)
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(OlistProdutoDetalhe.class));
    }

    public List<com.luan.FinancialSystem.service.dto.OlistProdutoResumo> listarTodosProdutos(Long userId) {
        List<com.luan.FinancialSystem.service.dto.OlistProdutoResumo> produtos = new ArrayList<>();
        int offset = 0;
        Integer total = null;

        while (total == null || produtos.size() < total) {
            int currentOffset = offset;
            OlistProdutosResponse pagina = executeWithRetryForUser(() -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/produtos")
                            .queryParam("limit", 100)
                            .queryParam("offset", currentOffset)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                    .retrieve()
                    .body(OlistProdutosResponse.class), userId);

            if (pagina == null || pagina.itens() == null || pagina.itens().isEmpty()) break;
            produtos.addAll(pagina.itens());
            if (pagina.paginacao() != null && pagina.paginacao().total() != null) total = pagina.paginacao().total();
            int limit = pagina.paginacao() != null && pagina.paginacao().limit() != null
                    ? pagina.paginacao().limit() : 100;
            offset += limit;
            if (pagina.itens().size() < limit) break;
        }

        return produtos;
    }

    public OlistSaldoEstoque obterEstoque(Long idProduto, Long userId) {
        return executeWithRetryForUser(() -> restClient.get()
                .uri("/estoque/{idProduto}", idProduto)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId))
                .retrieve()
                .body(OlistSaldoEstoque.class), userId);
    }

    private String bearerToken(Long userId) {
        return "Bearer " + olistAuthService.getValidAccessTokenForUser(userId);
    }

    private <T> T executeWithRetryForUser(Supplier<T> request, Long userId) {
        int attempts = 0;
        boolean refreshed = false;
        while (true) {
            try {
                awaitRequestSlot();
                return request.get();
            } catch (HttpClientErrorException.Unauthorized exception) {
                if (refreshed) throw exception;
                olistAuthService.refreshUserToken(userId);
                refreshed = true;
            } catch (HttpClientErrorException.TooManyRequests exception) {
                reduceRateAfterTooManyRequests();
                if (++attempts > MAX_RETRIES) throw exception;
                sleep(getRetryDelayMs(exception.getResponseHeaders()));
            }
        }
    }

    private String bearerToken() {
        System.out.println("[BACKEND OLIST API] Preparando header Authorization: Bearer [access_token salvo no banco].");
        return "Bearer " + olistAuthService.getValidAccessTokenForLoggedUser();
    }

    private <T> T executeWithRetry(Supplier<T> request) {
        int attempts = 0;
        boolean refreshedAfterUnauthorized = false;

        while (true) {
            try {
                awaitRequestSlot();
                return request.get();
            } catch (HttpClientErrorException.Unauthorized exception) {
                if (refreshedAfterUnauthorized) {
                    throw exception;
                }

                System.out.println("[BACKEND OLIST API] API recusou com 401. Vou renovar token usando refresh_token salvo e tentar de novo.");
                olistAuthService.refreshLoggedUserToken();
                refreshedAfterUnauthorized = true;
            } catch (HttpClientErrorException.TooManyRequests exception) {
                reduceRateAfterTooManyRequests();
                attempts++;

                if (attempts > MAX_RETRIES) {
                    throw exception;
                }

                System.out.println("[BACKEND OLIST API] Rate limit da API Olist/Tiny. Aguardando para tentar novamente...");
                sleep(getRetryDelayMs(exception.getResponseHeaders()));
            }
        }
    }

    private long getRetryDelayMs(HttpHeaders headers) {
        if (headers == null) {
            return DEFAULT_RETRY_DELAY_MS;
        }

        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_DELAY_MS;
        }

        try {
            return Math.max(Long.parseLong(retryAfter) * 1000L, DEFAULT_RETRY_DELAY_MS);
        } catch (NumberFormatException exception) {
            return DEFAULT_RETRY_DELAY_MS;
        }
    }

    private void awaitRequestSlot() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long waitMs = Math.max(0L, nextRequestAtMs - now);
            if (waitMs > 0L) {
                sleep(waitMs);
            }
            long intervalMs = (long) Math.ceil(60_000D / requestsPerMinute);
            nextRequestAtMs = System.currentTimeMillis() + intervalMs;
        }
    }

    private void reduceRateAfterTooManyRequests() {
        if (requestsPerMinute != REDUCED_REQUESTS_PER_MINUTE) {
            requestsPerMinute = REDUCED_REQUESTS_PER_MINUTE;
            System.out.println("[BACKEND OLIST API] HTTP 429 recebido. Limite reduzido de 100 para 90 requisicoes/minuto.");
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Importacao interrompida ao aguardar limite da API Tiny/Olist.", exception);
        }
    }
}
