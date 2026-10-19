package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:security_regression;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false", "spring.docker.compose.enabled=false",
    "app.auth.secure-cookie=true", "olist.webhook.secret=security-test-webhook",
    "app.mail.from=sender@example.com", "app.frontend-url=https://app.example.com"
})
class SecurityRegressionTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired EcommerceRepository ecommerces;
    @Autowired ProductRepository products;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordResetService resetService;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;
    final HttpClient client = HttpClient.newHttpClient();
    User alice, bob;
    String cookie;
    volatile String sentText;

    @BeforeEach void setup() throws Exception {
        alice = account("alice");
        bob = account("bob");
        doAnswer(invocation -> {
            sentText = ((SimpleMailMessage) invocation.getArgument(0)).getText(); return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        var login = request("POST", "/auth/login", "{\"email\":\"" + alice.getEmail()
                + "\",\"password\":\"original-password\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    }

    @AfterEach void cleanup() {
        authorizations.deleteAll(); revoked.deleteAll();
        products.deleteAll(); ecommerces.deleteAll(); tokens.deleteAll(); users.deleteAll();
    }

    User account(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setName(prefix); user.setPhone("11999999999");
        user.setPassword(encoder.encode("original-password"));
        user.setClient_secret("must-never-be-serialized");
        return users.saveAndFlush(user);
    }
    Ecommerce store(User user) {
        var store = new Ecommerce(); store.setName("Store"); store.setRate(1F); store.setFixed_rate(0F);
        store.setUser(user); return ecommerces.saveAndFlush(store);
    }
    HttpResponse<String> request(String method, String path, String json, String authCookie) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (authCookie != null) builder.header("Cookie", authCookie);
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    String rawToken() {
        return sentText.split("#token=")[1].split("\\n")[0];
    }

    @Test void resetInvalidatesPreviouslyAuthenticatedSessions() throws Exception {
        assertThat(request("GET", "/auth/me", null, cookie).statusCode()).isEqualTo(200);
        resetService.request(alice.getEmail());
        resetService.reset(rawToken(), "new-password");
        assertThat(request("GET", "/auth/me", null, cookie).statusCode()).isIn(401, 403);
    }

    @Test void bindsRecoveryTokenToEmailAndAllowsOnlyOneConcurrentReset() throws Exception {
        resetService.request(alice.getEmail());
        String raw = rawToken();
        alice.setEmail("changed-" + UUID.randomUUID() + "@example.com");
        users.saveAndFlush(alice);
        assertThatThrownBy(() -> resetService.reset(raw, "new-password"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var row = tokens.findById(alice.getId()).orElseThrow();
        row.setRequestedAt(java.time.Instant.now().minusSeconds(61)); tokens.saveAndFlush(row);
        resetService.request(alice.getEmail());
        String current = rawToken();
        var first = CompletableFuture.supplyAsync(() -> {
            try { resetService.reset(current, "new-password-one"); return true; }
            catch (org.springframework.web.server.ResponseStatusException exception) { return false; }
        });
        var second = CompletableFuture.supplyAsync(() -> {
            try { resetService.reset(current, "new-password-two"); return true; }
            catch (org.springframework.web.server.ResponseStatusException exception) { return false; }
        });
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
    }

    @Test void cannotEditOtherUsersStoresOrProductsOrCreateUsingTheirStore() throws Exception {
        var mine = store(alice); var theirs = store(bob);
        var product = new Product(); product.setName("Bob product"); product.setEcommerce(theirs);
        product = products.saveAndFlush(product);
        var editStore = request("PUT", "/ecommerce/editEcommerce?id=" + theirs.getId(),
                "{\"name\":\"Hacked\",\"rate\":0,\"fixed_rate\":0}", cookie);
        assertThat(editStore.statusCode()).isBetween(400, 499);
        assertThat(ecommerces.findById(theirs.getId()).orElseThrow().getName()).isEqualTo("Store");
        assertThat(request("DELETE", "/ecommerce/" + theirs.getId(), null, cookie).statusCode()).isBetween(400, 499);
        assertThat(request("PUT", "/product/update?id=" + product.getId(),
                "{\"name\":\"Hacked\",\"ecommerce\":{\"id\":" + mine.getId() + "}}", cookie).statusCode()).isBetween(400, 499);
        assertThat(request("DELETE", "/product/delete?id=" + product.getId(), null, cookie).statusCode()).isBetween(400, 499);
        assertThat(request("POST", "/product", "{\"name\":\"Injected\",\"ecommerce\":{\"id\":" + theirs.getId() + "}}", cookie).statusCode()).isBetween(400, 499);
        assertThat(products.findById(product.getId()).orElseThrow().getName()).isEqualTo("Bob product");
        String json = request("GET", "/ecommerce", null, cookie).body();
        assertThat(json).doesNotContain("password", "must-never-be-serialized", "client_secret", "\"user\"");
        assertThat(request("POST", "/product", "{\"name\":\"Valid\",\"ecommerce\":{\"id\":" + mine.getId() + "}}", cookie).statusCode()).isEqualTo(200);
    }

    @Test void registrationCannotOverwriteAnAccountBySupplyingItsId() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@example.com";
        var result = request("POST", "/auth/register", "{\"id\":" + bob.getId()
                + ",\"name\":\"New\",\"phone\":\"11999999999\",\"email\":\"" + email
                + "\",\"password\":\"new-password\"}", null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(users.findById(bob.getId()).orElseThrow().getEmail()).isEqualTo(bob.getEmail());
        assertThat(users.findByEmail(email).orElseThrow().getId()).isNotEqualTo(bob.getId());
    }

    @Test void requiresWebhookSecretAndRejectsCrossSiteWritesAndMalformedSessions() throws Exception {
        for (String path : List.of("/auth/olist-token/callback", "/olist/estoque/webhook", "/olist/vendas/webhook")) {
            assertThat(request("POST", path, "{}", null).statusCode()).isEqualTo(401);
        }
        var crossSite = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/logout"))
                .header("Cookie", cookie).header("Origin", "https://evil.example")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(crossSite, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/auth/me", null, "token=malformed").statusCode()).isIn(401, 403);
    }

    @Test void secureCookieAndGenericLoginFailures() throws Exception {
        var login = request("POST", "/auth/login", "{\"email\":\"" + alice.getEmail()
                + "\",\"password\":\"original-password\"}", null);
        assertThat(login.headers().firstValue("set-cookie").orElseThrow()).contains("Secure", "HttpOnly", "SameSite=Lax");
        assertThat(login.headers().firstValue("set-cookie").orElseThrow().split(";")[0]).isNotEqualTo(cookie);
        assertThat(login.body()).doesNotContain("\"token\":", "password", "client_secret");
        var wrongPassword = request("POST", "/auth/login", "{\"email\":\"" + alice.getEmail()
                + "\",\"password\":\"wrong-password\"}", null);
        var unknown = request("POST", "/auth/login", "{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}", null);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(unknown.body()).isEqualTo(wrongPassword.body());
    }
    @org.springframework.test.context.DynamicPropertySource
    static void providerUrl(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("olist.oauth.token-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/token");
    }
    static final java.util.concurrent.atomic.AtomicInteger providerCalls = new java.util.concurrent.atomic.AtomicInteger();
    static final com.sun.net.httpserver.HttpServer provider;
    static {
        try {
            provider = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/token", exchange -> {
                providerCalls.incrementAndGet();
                byte[] json = "{\"access_token\":\"verified-access\",\"refresh_token\":\"verified-refresh\",\"expires_in\":3600}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json); exchange.close();
            });
            provider.start();
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    @AfterAll static void stopProvider() { provider.stop(0); }
    @Autowired OlistAuthorizationRepository authorizations;
    @Autowired RevokedTokenRepository revoked;
    @Autowired CryptoService crypto;

    HttpResponse<String> callback(String state) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/olist-token/callback"))
                .header("Content-Type", "application/json").header("X-Webhook-Secret", "security-test-webhook")
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"provider-code\",\"clientId\":\"" + state + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    String authorize() throws Exception {
        var response = request("POST", "/auth/olist-authorize", null, cookie);
        assertThat(response.statusCode()).isEqualTo(200);
        String url = new tools.jackson.databind.ObjectMapper().readTree(response.body()).path("authorizationUrl").asText();
        assertThat(url).startsWith("https://accounts.tiny.com.br/");
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .filter(part -> part.startsWith("state=")).findFirst().orElseThrow().substring(6);
    }
    @Test void oauthUsesExpiringSingleUseRandomStateAndEncryptsProviderTokens() throws Exception {
        alice.setClient_id("test-olist-client");
        alice.setClient_secret(crypto.encrypt("provider-client-secret"));
        users.saveAndFlush(alice);
        String expired = authorize();
        assertThat(expired).hasSize(43).isNotEqualTo(alice.getClient_id());
        var row = authorizations.findById(alice.getId()).orElseThrow();
        assertThat(row.getStateHash()).isEqualTo(PasswordResetService.hash(expired));
        row.setExpiresAt(java.time.Instant.now().minusSeconds(1)); authorizations.saveAndFlush(row);
        assertThat(callback(expired).statusCode()).isEqualTo(400);
        assertThat(callback(alice.getClient_id()).statusCode()).isEqualTo(400);
        String state = authorize();
        int calls = providerCalls.get();
        var first = CompletableFuture.supplyAsync(() -> {
            try { return callback(state).statusCode(); } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        var second = CompletableFuture.supplyAsync(() -> {
            try { return callback(state).statusCode(); } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 400);
        assertThat(providerCalls.get() - calls).isEqualTo(1);
        assertThat(authorizations.findById(alice.getId())).isEmpty();
        assertThat(crypto.decrypt(users.findById(alice.getId()).orElseThrow().getOlist_access_token())).isEqualTo("verified-access");
    }
    @Test void logoutRevocationIsStoredAsAnExpiringHash() throws Exception {
        assertThat(request("POST", "/auth/logout", null, cookie).statusCode()).isEqualTo(200);
        String token = cookie.substring("token=".length());
        var row = revoked.findById(PasswordResetService.hash(token)).orElseThrow();
        assertThat(row.getExpiresAt()).isAfter(java.time.Instant.now());
        assertThat(row.getTokenHash()).isNotEqualTo(token);
        assertThat(request("POST", "/auth/forgot-password", "{\"email\":\"missing@example.com\"}", cookie).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/auth/me", null, cookie).statusCode()).isEqualTo(401);
    }
}
