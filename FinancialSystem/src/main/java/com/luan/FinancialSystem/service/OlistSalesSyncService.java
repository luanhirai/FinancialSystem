package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.repository.*;
import com.luan.FinancialSystem.service.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class OlistSalesSyncService {
    private static final Logger log = LoggerFactory.getLogger(OlistSalesSyncService.class);
    private final OlistClient client;
    private final OlistSalesWebhookService sales;
    private final UserRepository users;
    private final OlistSalesOrderRepository orders;
    private final AuthenticatedUserService auth;
    private final Map<Long, Status> jobs = new ConcurrentHashMap<>();
    public OlistSalesSyncService(OlistClient client, OlistSalesWebhookService sales, UserRepository users,
            OlistSalesOrderRepository orders, AuthenticatedUserService auth) {
        this.client=client; this.sales=sales; this.users=users; this.orders=orders; this.auth=auth;
    }
    public Status status() { return jobs.getOrDefault(auth.getLoggedUser().getId(), new Status()); }
    public Status start(LocalDate start, LocalDate end) {
        LocalDate today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if(start==null) start=today;
        if(end==null) end=today;
        if(start.isAfter(end)) throw new IllegalArgumentException("Periodo invalido.");
        return launch(auth.getLoggedUser().getId(),start,end);
    }
    @Scheduled(fixedDelay=120000, initialDelay=15000)
    public void scheduledSync() {
        LocalDate today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        for(var user: users.findOlistConnectedUsers()) launch(user.getId(),today,today);
    }
    private Status launch(Long userId, LocalDate start, LocalDate end) {
        Status job=jobs.computeIfAbsent(userId,id->new Status());
        synchronized(job) {
            if(job.running) return job;
            job.running=true; job.found=0; job.imported=0; job.failed=0; job.error=null;
            job.message="Buscando pedidos no Tiny...";
            job.startDate=start.toString(); job.endDate=end.toString();
            CompletableFuture.runAsync(()->synchronize(userId,start,end,job));
            return job;
        }
    }
    void synchronize(Long userId,LocalDate start,LocalDate end,Status job) {
        try {
            OlistPedidosResponse response=client.listarPedidos(userId,start,end);
            if(response==null || response.itens()==null)
                throw new IllegalStateException("Tiny nao retornou uma lista de pedidos.");
            job.found=response.itens().size();
            for(OlistPedidoResumo summary: response.itens()) {
                if(summary==null || summary.id()==null) continue;
                if(orders.existsByUserIdAndOlistOrderId(userId,summary.id())) continue;
                try {
                    sales.importFromApi(userId,client.obterPedidoCompleto(summary.id(),userId));
                    job.imported++;
                } catch(Exception ex) {
                    job.failed++;
                    job.error="Nao foi possivel recuperar um ou mais pedidos. A sincronizacao tentara novamente.";
                    log.warn("Falha ao recuperar pedido Olist: userId={}, orderId={}, tipo={}",
                            userId,summary.id(),ex.getClass().getSimpleName());
                }
            }
            job.message=job.imported+" pedido(s) recuperado(s); "+job.failed+" falha(s).";
        } catch(Exception ex) {
            job.error="Falha ao consultar o Tiny. Confira a conexao da conta e a permissao de leitura de pedidos.";
            job.message="Sincronizacao nao concluida.";
            log.warn("Falha na sincronizacao de vendas Olist: userId={}, tipo={}", userId,ex.getClass().getSimpleName());
        } finally { job.lastAttemptAt=Instant.now(); job.running=false; }
    }
    public static class Status {
        public volatile boolean running;
        public volatile int found, imported, failed;
        public volatile String message="Aguardando primeira sincronizacao.", error, startDate, endDate;
        public volatile Instant lastAttemptAt;
    }
}
