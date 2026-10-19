package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:password_reset_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false", "spring.docker.compose.enabled=false",
        "app.mail.from=finsysmavie@gmail.com", "app.frontend-url=http://localhost:3000"
})
class PasswordResetFlowTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;
    final HttpClient client = HttpClient.newHttpClient();
    String email;
    Long userId;
    String sentText;

    @BeforeEach void setup() {
        email = "reset-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setName("Reset test"); user.setPhone("11999999999"); user.setEmail(email);
        user.setPassword(encoder.encode("original-password"));
        userId = users.saveAndFlush(user).getId();
        sentText = null;
        doAnswer(invocation -> {
            sentText = ((SimpleMailMessage) invocation.getArgument(0)).getText();
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
    }

    @AfterEach void cleanup() {
        tokens.deleteById(userId);
        users.deleteById(userId);
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> forgot(String address) throws Exception {
        return post("/auth/forgot-password", "{\"email\":\"" + address + "\"}");
    }
    String token() {
        assertThat(sentText).contains("/redefinir-senha#token=");
        return sentText.split("#token=")[1].split("\\n")[0];
    }
    HttpResponse<String> reset(String raw, String password) throws Exception {
        return post("/auth/reset-password", "{\"token\":\"" + raw + "\",\"password\":\"" + password + "\"}");
    }

    @Test void recoversPasswordThroughPublicHttpEndpointsAndRejectsReuse() throws Exception {
        assertThat(forgot(email).statusCode()).isEqualTo(200);
        String raw = token();
        var stored = tokens.findById(userId).orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(PasswordResetService.hash(raw));
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(reset(raw, "new-password").statusCode()).isEqualTo(200);
        assertThat(encoder.matches("new-password", users.findById(userId).orElseThrow().getPassword())).isTrue();
        assertThat(post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"original-password\"}").statusCode()).isEqualTo(401);
        assertThat(post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"new-password\"}").statusCode()).isEqualTo(200);
        assertThat(reset(raw, "other-password").statusCode()).isEqualTo(400);
    }

    @Test void returnsGenericResponseForUnknownEmailsAndValidatesRequests() throws Exception {
        var known = forgot(email);
        var unknown = forgot("unknown-" + UUID.randomUUID() + "@example.com");
        assertThat(unknown.statusCode()).isEqualTo(200);
        assertThat(unknown.body()).isEqualTo(known.body());
        assertThat(post("/auth/forgot-password", "{\"email\":\"invalid\"}").statusCode()).isEqualTo(400);
        assertThat(post("/auth/reset-password", "{\"token\":\"invalid\",\"password\":\"short\"}").statusCode()).isEqualTo(400);
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test void throttlesAndInvalidatesPreviousLinkAfterNewRequest() throws Exception {
        forgot(email);
        String first = token();
        forgot(email);
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        var stored = tokens.findById(userId).orElseThrow();
        stored.setRequestedAt(Instant.now().minusSeconds(61));
        tokens.saveAndFlush(stored);
        assertThat(forgot(email).statusCode()).isEqualTo(200);
        String second = token();
        assertThat(second).isNotEqualTo(first);
        assertThat(reset(first, "new-password").statusCode()).isEqualTo(400);
        assertThat(reset(second, "new-password").statusCode()).isEqualTo(200);
    }

    @Test void rejectsExpiredTokenAndOversizedUtf8Password() throws Exception {
        forgot(email);
        String raw = token();
        assertThat(reset(raw, "á".repeat(37)).statusCode()).isEqualTo(400);
        var stored = tokens.findById(userId).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.saveAndFlush(stored);
        assertThat(reset(raw, "new-password").statusCode()).isEqualTo(400);
        assertThat(encoder.matches("original-password", users.findById(userId).orElseThrow().getPassword())).isTrue();
    }

    @Test void failedSmtpSendRollsBackTokenAndAllowsRetry() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(forgot(email).statusCode()).isEqualTo(503);
        assertThat(tokens.findById(userId)).isEmpty();
        doAnswer(invocation -> {
            sentText = ((SimpleMailMessage) invocation.getArgument(0)).getText();
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        assertThat(forgot(email).statusCode()).isEqualTo(200);
        assertThat(reset(token(), "new-password").statusCode()).isEqualTo(200);
    }
}
