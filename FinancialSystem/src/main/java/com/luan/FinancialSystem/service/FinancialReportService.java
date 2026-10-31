package com.luan.FinancialSystem.service;

import com.luan.FinancialSystem.entity.OlistSalesOrder;
import com.luan.FinancialSystem.repository.OlistSalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinancialReportService {
    private final OlistSalesOrderRepository orders;
    private final AuthenticatedUserService auth;
    private final ObjectMapper mapper;
    public FinancialReportService(OlistSalesOrderRepository orders, AuthenticatedUserService auth, ObjectMapper mapper) {
        this.orders=orders; this.auth=auth; this.mapper=mapper;
    }
    public record Option(String id, String name) {}
    public record Line(Long orderId, Long number, LocalDate date, String channel, String productId,
                       String product, String sku, BigDecimal quantity, BigDecimal revenue,
                       BigDecimal cost, BigDecimal fees, BigDecimal tax, BigDecimal profit, String pending) {}
    public record Totals(int orders, BigDecimal quantity, BigDecimal revenue, BigDecimal cost,
                         BigDecimal fees, BigDecimal tax, BigDecimal profit, int pending) {}
    public record Group(String id, String name, Totals totals) {}
    public record Report(LocalDate start, LocalDate end, String productId, String channel,
                         Totals totals, List<Group> products, List<Group> channels, List<Line> lines,
                         List<Option> productOptions, List<String> channelOptions, int cancelled) {}

    @Transactional(readOnly=true)
    public Report report(LocalDate start, LocalDate end, String productId, String channel) {
        return report(start,end,productId,channel,"orderDate","","");
    }
    @Transactional(readOnly=true)
    public Report report(LocalDate start, LocalDate end, String productId, String channel, String dateType, String search, String status) {
        if(!Set.of("orderDate","createdAt").contains(dateType)) throw new IllegalArgumentException("Tipo de data inválido.");
        if(start==null || end==null || start.isAfter(end)) throw new IllegalArgumentException("Informe um período válido.");
        String productFilter=productId==null?"":productId.trim();
        String channelFilter=channel==null?"":channel.trim();
        Long userId=auth.getLoggedUser().getId();
        List<OlistSalesOrder> selected="createdAt".equals(dateType)
            ? orders.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByOrderDateDescNumberDesc(userId,start.atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant(),end.plusDays(1).atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant())
            : orders.findByUserIdAndOrderDateBetweenOrderByOrderDateDescNumberDesc(userId,start,end);
        String query=Objects.toString(search,"").trim().toLowerCase(Locale.forLanguageTag("pt-BR"));
        selected=selected.stream().filter(o->status==null||status.isBlank()||status.equals(o.getStatusDescription()))
            .filter(o->query.isEmpty()||String.join(" ",Objects.toString(o.getNumber(),""),Objects.toString(o.getOlistOrderId(),""),Objects.toString(o.getEcommerceOrderId(),""),Objects.toString(o.getCustomerName(),""),Objects.toString(o.getEcommerceName(),"")).toLowerCase(Locale.forLanguageTag("pt-BR")).contains(query)).toList();
        List<Line> all=new ArrayList<>();
        Map<Long,Line> orderTotals=new LinkedHashMap<>();
        int cancelled=0;
        for(var order:selected) {
            if(Set.of("cancelado","2").contains(Objects.toString(order.getStatusCode(),"").toLowerCase(Locale.ROOT))) {cancelled++;continue;}
            var detail=parse(order.getOrderDetails()); var financial=parse(order.getFinancialDetails());
            String name=order.getEcommerceName()==null || order.getEcommerceName().isBlank()?"Sem ecommerce informado":order.getEcommerceName().trim();
            var items=detail.path("itens");
            List<BigDecimal> gross=new ArrayList<>(), units=new ArrayList<>();
            if(items.isArray()) for(var item:items) {
                var quantity=number(item,"quantidade"); var price=number(item,"valorUnitario");
                units.add(quantity); gross.add(quantity==null||price==null?null:quantity.multiply(price));
            }
            BigDecimal total=number(financial,"total");
            if(total==null) total=number(detail,"valorTotalPedido");
            BigDecimal freight=number(detail,"valorFrete");
            BigDecimal revenue=total==null?null:total.subtract(freight==null?BigDecimal.ZERO:freight);
            BigDecimal cost=number(financial,"custoProdutos");
            BigDecimal percent=number(financial,"taxaPercentual"), fixed=number(financial,"taxaFixa"), tax=number(financial,"impostoVenda");
            var quantity=sum(units);
            var pending=order.getRealValue()==null?financial.path("pendencia").asText("Cálculo financeiro pendente. Recalcule o pedido."):"";
            orderTotals.put(order.getId(),new Line(order.getId(),order.getNumber(),order.getOrderDate(),name,"","","",quantity,revenue,cost,add(percent,fixed),tax,order.getRealValue(),pending));
            if(!items.isArray() || items.isEmpty()) {
                all.add(new Line(order.getId(),order.getNumber(),order.getOrderDate(),name,"","Itens indisponíveis","",null,revenue,cost,add(percent,fixed),tax,order.getRealValue(),"Itens indisponíveis. Recalcule o pedido."));
                continue;
            }
            var revenues=allocate(revenue,gross); var percentages=allocate(percent,gross);
            var taxes=allocate(tax,gross); var fixedFees=allocate(fixed,units);
            var snapshots=financial.path("itens");
            for(int i=0;i<items.size();i++) {
                var item=items.get(i); var product=item.path("produto");
                String id=product.path("id").asText("");
                BigDecimal itemCost=null;
                if(snapshots.isArray() && i<snapshots.size() && snapshots.get(i).path("productId").asText("").equals(id)) itemCost=number(snapshots.get(i),"cost");
                if(items.size()==1 && itemCost==null) itemCost=cost;
                var fees=add(percentages.get(i),fixedFees.get(i));
                BigDecimal profit=!pending.isBlank()||revenues.get(i)==null||itemCost==null||fees==null||taxes.get(i)==null?null:revenues.get(i).subtract(itemCost).subtract(fees).subtract(taxes.get(i));
                String issue=pending;
                if(profit==null && issue.isBlank()) issue="Detalhamento financeiro por produto pendente. Recalcule o pedido.";
                all.add(new Line(order.getId(),order.getNumber(),order.getOrderDate(),name,id,product.path("descricao").asText("Produto sem descrição"),product.path("sku").asText(""),units.get(i),revenues.get(i),itemCost,fees,taxes.get(i),profit,issue));
            }
        }
        List<Option> options=all.stream().filter(l->!l.productId().isBlank()).collect(java.util.stream.Collectors.toMap(Line::productId,l->new Option(l.productId(),l.product()),(a,b)->a,TreeMap::new)).values().stream().toList();
        var channelOptions=all.stream().map(Line::channel).distinct().sorted().toList();
        var lines=all.stream().filter(l->productFilter.isBlank()||productFilter.equals(l.productId())).filter(l->channelFilter.isBlank()||channelFilter.equals(l.channel())).toList();
        Set<Long> ids=new HashSet<>(); lines.forEach(l->ids.add(l.orderId()));
        var basis=productFilter.isBlank()?orderTotals.values().stream().filter(l->ids.contains(l.orderId())).toList():lines;
        return new Report(start,end,productFilter,channelFilter,totals(basis),groups(lines,true),groups(basis,false),lines,options,channelOptions,cancelled);
    }
    private List<Group> groups(List<Line> lines,boolean product) {
        Map<String,List<Line>> grouped=new TreeMap<>();
        for(var line:lines) grouped.computeIfAbsent(product?line.productId():line.channel(),key->new ArrayList<>()).add(line);
        return grouped.entrySet().stream().map(e->new Group(e.getKey(),product?e.getValue().getFirst().product():e.getKey(),totals(e.getValue()))).toList();
    }
    private Totals totals(List<Line> lines) {
        return new Totals((int)lines.stream().map(Line::orderId).distinct().count(),sum(lines.stream().map(Line::quantity).toList()),
            sum(lines.stream().map(Line::revenue).toList()),sum(lines.stream().map(Line::cost).toList()),sum(lines.stream().map(Line::fees).toList()),
            sum(lines.stream().map(Line::tax).toList()),sum(lines.stream().map(Line::profit).toList()),(int)lines.stream().filter(l->!l.pending().isBlank()).map(Line::orderId).distinct().count());
    }
    private BigDecimal sum(List<BigDecimal> values) {if(!values.isEmpty() && values.stream().allMatch(Objects::isNull)) return null; return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BigDecimal add(BigDecimal a,BigDecimal b) {return a==null||b==null?null:a.add(b);}
    private JsonNode parse(String json) {
        try {var node=json==null?null:mapper.readTree(json);return node!=null && node.isObject()?node:mapper.createObjectNode();}
        catch(RuntimeException e) {return mapper.createObjectNode();}
    }
    private BigDecimal number(JsonNode node,String key) {
        try {var value=node.path(key); return value.isNull()||value.isMissingNode()?null:new BigDecimal(value.asText());}
        catch(NumberFormatException e) {return null;}
    }
    // Allocate rounded cents cumulatively, preserving the exact order total.
    static List<BigDecimal> allocate(BigDecimal total,List<BigDecimal> weights) {
        List<BigDecimal> result=new ArrayList<>();
        if(total==null || weights.isEmpty() || weights.stream().anyMatch(w->w==null||w.signum()<0)) {for(int i=0;i<weights.size();i++)result.add(null);return result;}
        BigDecimal denominator=weights.stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if(denominator.signum()==0) {for(int i=0;i<weights.size();i++)result.add(total.signum()==0?BigDecimal.ZERO:null);return result;}
        BigDecimal accumulated=BigDecimal.ZERO, previous=BigDecimal.ZERO;
        for(var weight:weights) {
            accumulated=accumulated.add(weight);
            BigDecimal current=total.multiply(accumulated).divide(denominator,2,RoundingMode.HALF_UP);
            result.add(current.subtract(previous)); previous=current;
        }
        return result;
    }
}