package com.luan.FinancialSystem.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Both known and unknown accounts return before lookup/SMTP, avoiding timing enumeration. */
@Service
public class PasswordResetDeliveryService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetDeliveryService.class);
    private final PasswordResetService service;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "password-reset-delivery");
                thread.setDaemon(true);
                return thread;
            });
    public PasswordResetDeliveryService(PasswordResetService service) { this.service = service; }
    public void submit(String email) {
        service.checkMailConfiguration();
        try {
            executor.execute(() -> {
                try { service.request(email); }
                catch (RuntimeException exception) {
                    // Do not log addresses, message bodies, secrets or provider responses.
                    logger.warn("Password recovery delivery failed; check SMTP availability and credentials.");
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde alguns minutos e tente novamente.");
        }
    }
    @PreDestroy void shutdown() { executor.shutdownNow(); }
}
