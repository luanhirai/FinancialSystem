package com.luan.FinancialSystem.service;
import com.luan.FinancialSystem.entity.*;
import com.luan.FinancialSystem.repository.*;
import tools.jackson.databind.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;

@Service
public class SalesOrderFinancialService {
    private final OlistClient client;
    private final ProductRepository products;
    private final EcommerceRepository ecommerces;
    private final PolicySettingRepository policies;
    private final ObjectMapper mapper;
    public SalesOrderFinancialService(OlistClient client, ProductRepository products, EcommerceRepository ecommerces, PolicySettingRepository policies, ObjectMapper mapper) {
        this.client=client; this.products=products; this.ecommerces=ecommerces; this.policies=policies; this.mapper=mapper;
    }
    public void enrich(OlistSalesOrder order) {
        Long userId=order.getUser().getId();
        JsonNode data=client.obterPedidoCompleto(order.getOlistOrderId(),userId);
        if(data==null || !data.isObject()) throw new IllegalStateException("Pedido completo indisponivel na Olist.");
        applyDetails(order, data);
    }
    public void applyDetails(OlistSalesOrder order, JsonNode data) {
        Long userId = order.getUser().getId();
        order.setOrderDetails(data.toString());
        order.setRealValue(null);
        Map<String,Object> result=new LinkedHashMap<>();
        try {
            BigDecimal total=required(data,"valorTotalPedido");
            result.put("total",total);
            Ecommerce channel=ecommerces.findByUserId(userId).stream()
                .filter(e->e.getName()!=null && e.getName().trim().equalsIgnoreCase(order.getEcommerceName()))
                .findFirst().orElseThrow(()->new IllegalStateException("Cadastre o ecommerce e suas taxas."));
            PolicySetting policy=policies.findByUserId(userId).stream().findFirst()
                .orElseThrow(()->new IllegalStateException("Cadastre o imposto de venda."));
            if(channel.getRate()==null || channel.getFixed_rate()==null || policy.getRate()==null ||
                channel.getRate()<0 || channel.getFixed_rate()<0 || policy.getRate()<0)
                throw new IllegalStateException("Configure taxas e impostos validos.");
            JsonNode items=data.path("itens");
            if(!items.isArray() || items.isEmpty()) throw new IllegalStateException("Itens indisponiveis.");
            List<Map<String,Object>> itemCosts = new ArrayList<>();
            BigDecimal costs=BigDecimal.ZERO, units=BigDecimal.ZERO;
            for(JsonNode item:items) {
                String id=item.path("produto").path("id").asText();
                Product product=products.findByIdOlistAndUserId(id,userId)
                    .orElseThrow(()->new IllegalStateException("Cadastre o custo do produto "+id+"."));
                if(product.getCost()==null || product.getCost()<0) throw new IllegalStateException("Custo indisponivel: "+product.getName());
                BigDecimal quantity=required(item,"quantidade");
                if(quantity.signum()<=0) throw new IllegalStateException("Quantidade invalida.");
                BigDecimal itemCost = number(product.getCost()).multiply(quantity);
                itemCosts.add(Map.of("index", itemCosts.size(), "productId", id, "cost", money(costs.add(itemCost)).subtract(money(costs))));
                costs=costs.add(itemCost); units=units.add(quantity);
            }
            BigDecimal freight=data.path("valorFrete").isMissingNode() || data.path("valorFrete").isNull() ? BigDecimal.ZERO : required(data,"valorFrete");
            BigDecimal revenue=total.subtract(freight);
            BigDecimal percentage=money(revenue.multiply(number(channel.getRate())).divide(new BigDecimal("100")));
            BigDecimal fixed=money(units.multiply(number(channel.getFixed_rate())));
            BigDecimal tax=money(revenue.multiply(number(policy.getRate())).divide(new BigDecimal("100")));
            result.put("itens", itemCosts);
            result.put("custoProdutos",money(costs)); result.put("taxaPercentual",percentage);
            result.put("taxaFixa",fixed); result.put("impostoVenda",tax); result.put("freteExcluido",freight);
            order.setRealValue(money(revenue.subtract(money(costs)).subtract(percentage).subtract(fixed).subtract(tax)));
        } catch(IllegalStateException e) { result.put("pendencia",e.getMessage()); }
        order.setFinancialDetails(mapper.writeValueAsString(result));
    }
    private BigDecimal required(JsonNode node,String key) {
        try { return new BigDecimal(node.path(key).asText()); }
        catch(NumberFormatException e) { throw new IllegalStateException("Valor indisponivel: "+key); }
    }
    private BigDecimal number(Number value) { return new BigDecimal(value.toString()); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
}
