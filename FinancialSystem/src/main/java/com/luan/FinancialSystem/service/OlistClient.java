package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.service.dto.OlistPedidoDetalhe;
import com.luan.FinancialSystem.service.dto.OlistPedidosResponse;
import com.luan.FinancialSystem.service.dto.OlistProdutoDetalhe;
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
    private static final long DEFAULT_RETRY_DELAY_MS = 2500L;
    private static final int PEDIDOS_PAGE_LIMIT = 100;

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

    public OlistProdutoDetalhe obterProduto(Long idProduto) {
        System.out.println("[BACKEND OLIST API] Buscando produto " + idProduto + " na API Olist/Tiny usando token salvo no banco.");
        return executeWithRetry(() -> restClient.get()
                .uri("/produtos/{idProduto}", idProduto)
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(OlistProdutoDetalhe.class));
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
                return request.get();
            } catch (HttpClientErrorException.Unauthorized exception) {
                if (refreshedAfterUnauthorized) {
                    throw exception;
                }

                System.out.println("[BACKEND OLIST API] API recusou com 401. Vou renovar token usando refresh_token salvo e tentar de novo.");
                olistAuthService.refreshLoggedUserToken();
                refreshedAfterUnauthorized = true;
            } catch (HttpClientErrorException.TooManyRequests exception) {
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

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Importacao interrompida ao aguardar limite da API Tiny/Olist.", exception);
        }
    }
}
