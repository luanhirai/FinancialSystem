package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTests {
    UserRepository users = mock(UserRepository.class);
    PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    JavaMailSender mail = mock(JavaMailSender.class);
    PasswordResetService service;
    User user;
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        service = new PasswordResetService(users, tokens, encoder, provider, "sender@gmail.com", "https://finsys.example");
        user = new User(); user.setId(1L); user.setEmail("user@example.com");
        when(users.findByEmailForPasswordReset(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.findById(1L)).thenReturn(Optional.empty());
    }
    @Test void sendsRealLinkAndStoresOnlyHash() {
        service.request(user.getEmail());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        String raw = message.getValue().getText().split("#token=")[1].split("\n")[0];
        var saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).saveAndFlush(saved.capture());
        assertThat(raw).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(1700));
        assertThat(message.getValue().getTo()).containsExactly(user.getEmail());
    }
    @Test void unknownEmailDoesNotSend() {
        service.request("unknown@example.com");
        verifyNoInteractions(mail);
    }
    @Test void throttlesRepeatedRequests() {
        var reset = new PasswordResetToken(); reset.setRequestedAt(Instant.now());
        when(tokens.findById(1L)).thenReturn(Optional.of(reset));
        service.request(user.getEmail());
        verifyNoInteractions(mail);
    }
    @Test void consumesTokenAndEncodesPassword() {
        String raw = "a".repeat(43);
        var reset = new PasswordResetToken(); reset.setUser(user); reset.setAccountEmail(user.getEmail());
        reset.setTokenHash(PasswordResetService.hash(raw)); reset.setExpiresAt(Instant.now().plusSeconds(100));
        when(tokens.findForUpdate(PasswordResetService.hash(raw))).thenAnswer(invocation ->
                reset.getTokenHash() == null ? Optional.empty() : Optional.of(reset));
        when(encoder.encode("new-password")).thenReturn("encoded");
        service.reset(raw, "new-password");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(reset.getTokenHash()).isNull();
        assertThatThrownBy(() -> service.reset(raw, "new-password")).isInstanceOf(ResponseStatusException.class);
        verify(users, times(1)).save(user);
    }
    @Test void rejectsExpiredAndInvalidTokensAndWeakPasswords() {
        String raw = "a".repeat(43);
        var reset = new PasswordResetToken(); reset.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokens.findForUpdate(PasswordResetService.hash(raw))).thenReturn(Optional.of(reset));
        assertThatThrownBy(() -> service.reset(raw, "new-password")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reset("invalid", "new-password")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reset(raw, "short")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.reset(raw, "á".repeat(37))).isInstanceOf(ResponseStatusException.class);
        verify(users, never()).save(any());
    }
    @Test void reportsMailFailure() {
        doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.request(user.getEmail())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Não foi possível enviar");
    }
}
