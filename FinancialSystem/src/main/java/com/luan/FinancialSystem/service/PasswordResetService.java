package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.PasswordResetToken;
import com.luan.FinancialSystem.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String from;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
            PasswordEncoder encoder, ObjectProvider<JavaMailSender> mailProvider,
            @Value("${app.mail.from:}") String from,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailProvider = mailProvider;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void checkMailConfiguration() {
        JavaMailSender sender = mailProvider.getIfAvailable();
        if (sender == null || from.isBlank()
                || (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp
                    && (smtp.getPassword() == null || smtp.getPassword().isBlank()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio de e-mail indisponível.");
        }
    }

    @Transactional
    public void request(String email) {
        JavaMailSender sender = mailProvider.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O envio de e-mail ainda não está configurado. Contate o administrador.");
        }
        var user = users.findByEmailForPasswordReset(email.trim());
        if (user.isEmpty()) return;
        Instant now = Instant.now();
        var reset = tokens.findById(user.get().getId()).orElseGet(PasswordResetToken::new);
        if (reset.getRequestedAt() != null && reset.getRequestedAt().plusSeconds(60).isAfter(now)) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        reset.setUser(user.get());
        reset.setAccountEmail(user.get().getEmail());
        reset.setTokenHash(hash(token));
        reset.setRequestedAt(now);
        reset.setExpiresAt(now.plusSeconds(1800));
        tokens.saveAndFlush(reset);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.get().getEmail());
        message.setSubject("FINSYS - Recuperação de senha");
        message.setText("Recebemos uma solicitação para redefinir sua senha no FINSYS.\n\n"
                + "Acesse o link abaixo, válido por 30 minutos e para um único uso:\n"
                + frontendUrl + "/redefinir-senha#token=" + token
                + "\n\nSe você não solicitou a alteração, ignore este e-mail. Sua senha permanece a mesma.");
        try {
            sender.send(message);
        } catch (MailException exception) {
            // Do not log the message: it contains the recovery secret.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar o e-mail. Tente novamente mais tarde.");
        }
    }

    @Transactional
    public void reset(String token, String password) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
        }
        var reset = tokens.findForUpdate(hash(token)).orElseThrow(this::invalidToken);
        if (reset.getExpiresAt() == null || !reset.getExpiresAt().isAfter(Instant.now())) throw invalidToken();
        if (!reset.getUser().getEmail().equals(reset.getAccountEmail())) throw invalidToken();
        reset.getUser().setPassword(encoder.encode(password));
        users.save(reset.getUser());
        reset.setTokenHash(null);
        reset.setExpiresAt(null);
        tokens.save(reset);
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Link inválido ou expirado. Solicite uma nova recuperação de senha.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
