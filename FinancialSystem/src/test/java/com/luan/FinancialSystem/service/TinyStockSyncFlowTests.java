package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import com.luan.FinancialSystem.service.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:stock_sync_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false", "spring.docker.compose.enabled=false"
})
class TinyStockSyncFlowTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired EcommerceRepository stores;
    @Autowired PasswordEncoder encoder;
    @MockitoBean OlistClient client;
    final HttpClient http = HttpClient.newHttpClient();
    final tools.jackson.databind.ObjectMapper json = new tools.jackson.databind.ObjectMapper();

    User account(String name) {
        User user = new User(); user.setName(name); user.setEmail(name + "@example.com");
        user.setPhone("11999999999"); user.setPassword(encoder.encode("original-password"));
        return users.saveAndFlush(user);
    }
    Ecommerce store(User owner, String name) {
        var store = new Ecommerce(); store.setName(name); store.setRate(12F);
        store.setFixed_rate(5F); store.setUser(owner); return stores.saveAndFlush(store);
    }
    Product product(Ecommerce store, String tinyId, int quantity) {
        var product = new Product(); product.setName("Custom name");
        product.setId_olist(tinyId); product.setQuantity(quantity);
        product.setCost(22F); product.setOriginal_price(80F); product.setEcommerce(store);
        return products.saveAndFlush(product);
    }
    HttpResponse<String> request(String method, String path, String body, String cookie) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    tools.jackson.databind.JsonNode awaitFinished(String cookie) throws Exception {
        for (int i = 0; i < 150; i++) {
            var status = json.readTree(request("GET", "/olist/produtos/atualizar-estoque/status", null, cookie).body());
            if (!status.path("running").asBoolean()) return status;
            Thread.sleep(20);
        }
        throw new AssertionError("Stock sync did not finish");
    }

    @Test void authenticatedSyncUpdatesOnlyOwnersStockAndCreatesMissingProductsWithoutDuplicates() throws Exception {
        var alice = account("stock-alice");
        var bob = account("stock-bob");
        var aliceStore = store(alice, "Custom store");
        var bobStore = store(bob, "Bob store");
        var original = product(aliceStore, " 123 ", 10);
        var bobsProduct = product(bobStore, "123", 88);
        var bobsOtherProduct = product(bobStore, "456", 99);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.listarTodosProdutos(alice.getId())).thenReturn(List.of(
                new OlistProdutoResumo(123L, "s1", "Provider name", "S", new OlistPrecos(15D, null, 5D, null), null),
                new OlistProdutoResumo(456L, "s2", "New product", "S", new OlistPrecos(30D, null, 12D, null), null)));
        when(client.obterEstoque(123L, alice.getId())).thenAnswer(invocation -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS);
            return new OlistSaldoEstoque(123L, null, null, null, 12D, 5D, 7D, null);
        });
        when(client.obterEstoque(456L, alice.getId())).thenReturn(
                new OlistSaldoEstoque(456L, null, null, null, 4D, 4D, 0D, null));
        var login = request("POST", "/auth/login", "{\"email\":\"" + alice.getEmail() + "\",\"password\":\"original-password\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        try {
            assertThat(request("POST", "/olist/produtos/atualizar-estoque", null, null).statusCode()).isIn(401, 403);
            assertThat(request("POST", "/olist/produtos/atualizar-estoque", null, cookie).statusCode()).isEqualTo(200);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(request("POST", "/olist/produtos/atualizar-estoque", null, cookie).statusCode()).isEqualTo(200);
            var edited = products.findById(original.getId()).orElseThrow();
            edited.setCost(25F); edited.setName("Changed during synchronization"); products.saveAndFlush(edited);
            release.countDown();
            var status = awaitFinished(cookie);
            assertThat(status.path("updated").asInt()).isEqualTo(1);
            assertThat(status.path("created").asInt()).isEqualTo(1);
            assertThat(status.path("failed").asInt()).isZero();
            var updated = products.findById(original.getId()).orElseThrow();
            assertThat(updated.getQuantity()).isEqualTo(7);
            assertThat(updated.getName()).isEqualTo("Changed during synchronization");
            assertThat(updated.getCost()).isEqualTo(25F);
            assertThat(updated.getOriginal_price()).isEqualTo(80F);
            assertThat(updated.getEcommerce().getId()).isEqualTo(aliceStore.getId());
            assertThat(products.findById(bobsProduct.getId()).orElseThrow().getQuantity()).isEqualTo(88);
            assertThat(products.findById(bobsOtherProduct.getId()).orElseThrow().getQuantity()).isEqualTo(99);
            var created = products.findByIdOlistAndUserId("456", alice.getId()).orElseThrow();
            assertThat(created.getQuantity()).isZero();
            assertThat(created.getName()).isEqualTo("New product");
            assertThat(created.getCost()).isEqualTo(12F);
            assertThat(created.getEcommerce().getUser().getId()).isEqualTo(alice.getId());
            verify(client, times(1)).listarTodosProdutos(alice.getId());

            assertThat(request("POST", "/olist/produtos/atualizar-estoque", null, cookie).statusCode()).isEqualTo(200);
            var repeated = awaitFinished(cookie);
            assertThat(repeated.path("created").asInt()).isZero();
            assertThat(repeated.path("updated").asInt()).isEqualTo(2);
            assertThat(products.findProductsByUser(alice.getId())).hasSize(2);
        } finally {
            release.countDown();
            awaitFinished(cookie);
            products.deleteAll(); stores.deleteAll(); users.deleteAll();
        }
    }
}
