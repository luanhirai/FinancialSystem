package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:financial_reports;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false","spring.docker.compose.enabled=false"
})
class FinancialReportFlowTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired OlistSalesOrderRepository orders;
    @Autowired PasswordEncoder encoder;
    @Autowired ProductRepository products;
    @Autowired EcommerceRepository ecommerces;
    @Autowired PolicySettingRepository policies;
    @Autowired ObjectMapper mapper;
    @MockitoBean OlistClient olist;
    final HttpClient client=HttpClient.newHttpClient();
    String cookie;
    User alice,bob;
    @BeforeEach void setup() throws Exception {
        alice=account("alice");bob=account("bob");
        save(alice,100L,"Loja Alice","11");save(bob,200L,"Loja Bob","22");
        var login=client.send(HttpRequest.newBuilder(uri("/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"email\":\""+alice.getEmail()+"\",\"password\":\"password-test\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);cookie=login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    }
    @AfterEach void cleanup(){orders.deleteAll();products.deleteAll();policies.deleteAll();ecommerces.deleteAll();users.deleteAll();}
    User account(String name){var u=new User();u.setName(name);u.setEmail(name+UUID.randomUUID()+"@example.com");u.setPhone("11999999999");u.setPassword(encoder.encode("password-test"));return users.saveAndFlush(u);}
    void save(User user,long number,String channel,String product){
        var o=new OlistSalesOrder();o.setUser(user);o.setOlistOrderId(number);o.setNumber(number);o.setAccountDocument("");o.setEventType("sincronizacao_api");o.setWebhookVersion("api-v3");o.setOrderDate(LocalDate.of(2026,10,10));o.setStatusCode("aprovado");o.setEcommerceName(channel);o.setRawPayload("{}");o.setCreatedAt(Instant.now());o.setUpdatedAt(Instant.now());o.setLastWebhookAt(Instant.now());
        o.setOrderDetails("{\"valorTotalPedido\":100,\"itens\":[{\"produto\":{\"id\":"+product+",\"descricao\":\"=HYPERLINK(unsafe) & <produto>\",\"sku\":\"SKU\"},\"quantidade\":2,\"valorUnitario\":50}]}");
        o.setFinancialDetails("{\"total\":100,\"custoProdutos\":40,\"taxaPercentual\":10,\"taxaFixa\":4,\"impostoVenda\":5}");o.setRealValue(new java.math.BigDecimal("41.00"));orders.saveAndFlush(o);
    }
    URI uri(String path){return URI.create("http://localhost:"+port+path);}
    String path(){return "/olist/relatorios/financeiro?dataInicial=2026-10-01&dataFinal=2026-10-31";}
    HttpResponse<byte[]> get(String path,boolean login)throws Exception {var request=HttpRequest.newBuilder(uri(path));if(login)request.header("Cookie",cookie);return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
    @Test void readsPersistedOrdersThroughAuthenticatedEndpointAndFiltersForeignData()throws Exception {
        var response=get(path(),true);assertThat(response.statusCode()).isEqualTo(200);
        var data=mapper.readTree(response.body());assertThat(data.path("totals").path("orders").asInt()).isEqualTo(1);
        assertThat(data.path("totals").path("profit").decimalValue()).isEqualByComparingTo("41");
        assertThat(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("Loja Bob");
        var filtered=get(path()+"&produtoId=22",true);assertThat(mapper.readTree(filtered.body()).path("lines").size()).isZero();
        var period=get("/olist/relatorios/financeiro?dataInicial=2026-09-01&dataFinal=2026-09-30",true);assertThat(mapper.readTree(period.body()).path("lines").size()).isZero();
        assertThat(get(path(),false).statusCode()).isIn(401,403);
        assertThat(get("/olist/relatorios/financeiro?dataInicial=2026-11-01&dataFinal=2026-10-01",true).statusCode()).isEqualTo(400);
    }
    @Test void recalculationPersistsItemCostsAndReportKeepsSnapshotUntilExplicitRecalculation()throws Exception {
        var channel=new Ecommerce();channel.setUser(alice);channel.setName("Loja Alice");channel.setRate(10f);channel.setFixed_rate(2f);channel=ecommerces.saveAndFlush(channel);
        var product=new Product();product.setEcommerce(channel);product.setId_olist("11");product.setName("Produto");product.setCost(20f);product=products.saveAndFlush(product);
        var policy=new PolicySetting();policy.setUser(alice);policy.setRate(5f);policies.saveAndFlush(policy);
        var request=HttpRequest.newBuilder(uri("/olist/vendas/pedidos/recalcular")).header("Cookie",cookie).POST(HttpRequest.BodyPublishers.noBody()).build();
        var recalculated=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(recalculated.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(recalculated.body()).path("total").asInt()).isEqualTo(1);
        var saved=orders.findByUserId(alice.getId()).getFirst();
        assertThat(saved.getFinancialDetails()).contains("itens","productId");
        var bobOrder=orders.findByUserId(bob.getId()).getFirst();assertThat(bobOrder.getFinancialDetails()).doesNotContain("itens");
        product.setCost(99f);products.saveAndFlush(product);
        var report=mapper.readTree(get(path()+"&produtoId=11",true).body());
        assertThat(report.path("totals").path("cost").decimalValue()).isEqualByComparingTo("40");
        assertThat(report.path("totals").path("profit").decimalValue()).isEqualByComparingTo("41");
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        report=mapper.readTree(get(path()+"&produtoId=11",true).body());
        assertThat(report.path("totals").path("cost").decimalValue()).isEqualByComparingTo("198");
        assertThat(report.path("totals").path("profit").decimalValue()).isEqualByComparingTo("-117");
        org.mockito.Mockito.verifyNoInteractions(olist);
    }
    @Test void downloadsRealXlsxWithSameFiltersNumericCellsAndSafeText()throws Exception {
        String export=path().replace("financeiro?","financeiro/excel?")+"&produtoId=11";
        var response=get(export,true);assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("spreadsheetml");
        Map<String,String> files=new HashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){var bytes=zip.readAllBytes();files.put(entry.getName(),new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
        }
        assertThat(files).containsKeys("[Content_Types].xml","xl/workbook.xml","xl/worksheets/sheet1.xml","xl/worksheets/sheet2.xml");
        assertThat(files.get("xl/worksheets/sheet2.xml")).contains("<v>41.00</v>","=HYPERLINK(unsafe) &amp; &lt;produto&gt;").doesNotContain("<f>","Loja Bob");
        assertThat(get(export,false).statusCode()).isIn(401,403);
        var foreign=get(export.replace("produtoId=11","produtoId=22"),true);
        try(var zip=new ZipInputStream(new ByteArrayInputStream(foreign.body()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("xl/worksheets/sheet2.xml"))assertThat(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("HYPERLINK");}
    }
}