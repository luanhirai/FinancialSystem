"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { authFetch } from "@/lib/api";
import Sidebar from "../components/page";
import "../dashboard/dashboard.css";
import "./orders.css";

const today = () => new Intl.DateTimeFormat("en-CA", {timeZone:"America/Sao_Paulo", year:"numeric", month:"2-digit", day:"2-digit"}).format(new Date());
const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const money = value => value == null ? "Pendente" : currency.format(value);
const dateLabel = value => value ? value.split("-").reverse().join("/") : "Sem data";
function parse(value) { try { return JSON.parse(value) || {}; } catch { return {}; } }
function Fields({ value }) {
  if (value == null) return <span>Sem informa??o</span>;
  if (typeof value !== "object") return <span>{String(value)}</span>;
  return <dl className="order-fields">{Object.entries(value).map(([key, child]) =>
    <div key={key}><dt>{key}</dt><dd><Fields value={child} /></dd></div>)}</dl>;
}

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState([]);
  const [sync, setSync] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const [status, setStatus] = useState("");
  const [dateType, setDateType] = useState("orderDate");
  const [selected, setSelected] = useState(null);

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      if (start && end && start > end) return;
      const params = new URLSearchParams({dataInicial:start || end || today(), dataFinal:end || start || today(), tipoData:dateType});
      const response = await authFetch("/olist/vendas/pedidos?" + params);
      if (response.status === 401) { router.push("/login"); return; }
      if (!response.ok) throw new Error("N?o foi poss?vel carregar os pedidos.");
      setOrders(await response.json());
      const syncResponse = await authFetch("/olist/vendas/sincronizacao");
      if (syncResponse.ok) setSync(await syncResponse.json());
      setError("");
    } catch (exception) { setError(exception.message); }
    finally { setLoading(false); setRefreshing(false); }
  }, [router, start, end, dateType]);

  useEffect(() => {
    load();
    const timer = setInterval(load, 15000);
    return () => clearInterval(timer);
  }, [load]);

  const getDate = useCallback(order => {
    if (dateType === "orderDate") return order.orderDate || "";
    if (!order.createdAt) return "";
    // Registration dates use the business timezone, independent of browser timezone.
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit",
    }).formatToParts(new Date(order.createdAt));
    const part = type => parts.find(p => p.type === type)?.value;
    return part("year") + "-" + part("month") + "-" + part("day");
  }, [dateType]);

  const synchronize = async () => {
    if (start && end && start > end) { setError("Informe um periodo valido."); return; }
    try {
      const params = new URLSearchParams();
      if (start) params.set("dataInicial", start);
      if (end) params.set("dataFinal", end);
      const response = await authFetch("/olist/vendas/sincronizacao?" + params, {method:"POST"});
      if (!response.ok) throw new Error("Nao foi possivel iniciar a recuperacao de pedidos.");
      setSync(await response.json()); setError("");
    } catch (exception) { setError(exception.message); }
  };

  const invalidPeriod = !!(start && end && start > end);
  const statuses = [...new Set(orders.map(o => o.statusDescription).filter(Boolean))].sort();
  const groups = useMemo(() => {
    const result = new Map();
    if (invalidPeriod) return [];
    const query = search.trim().toLocaleLowerCase("pt-BR");
    for (const order of orders) {
      const date = getDate(order);
      const text = [order.number, order.olistOrderId, order.ecommerceOrderId, order.customerName, order.ecommerceName]
        .join(" ").toLocaleLowerCase("pt-BR");
      if (query && !text.includes(query)) continue;
      if (status && order.statusDescription !== status) continue;
      if ((start && date < start) || (end && date > end)) continue;
      if (!result.has(date)) result.set(date, []);
      result.get(date).push(order);
    }
    return [...result.entries()].sort(([a], [b]) => b.localeCompare(a));
  }, [orders, search, status, start, end, getDate, invalidPeriod]);
  const count = groups.reduce((sum, [, rows]) => sum + rows.length, 0);

  return <div className="dashboard-layout"><Sidebar /><main className="orders-main">
    <header className="orders-heading"><div><h1>Pedidos</h1>
      <p>Todos os pedidos cadastrados no sistema, agrupados por data. Atualiza??o autom?tica a cada 15 segundos.</p>
    </div><button disabled={refreshing} onClick={load}>{refreshing ? "Atualizando..." : "Atualizar"}</button></header>
    <section className="orders-filters glass" aria-label="Filtros de pedidos">
      <label className="orders-search">Buscar<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Pedido, cliente ou ecommerce" /></label>
      <label>Agrupar por<select value={dateType} onChange={e => setDateType(e.target.value)}><option value="orderDate">Data do pedido</option><option value="createdAt">Data de cadastro</option></select></label>
      <label>De<input type="date" value={start} onChange={e => setStart(e.target.value)} /></label>
      <label>At?<input type="date" value={end} onChange={e => setEnd(e.target.value)} /></label>
      <label>Status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos</option>{statuses.map(s => <option key={s}>{s}</option>)}</select></label>
      <button onClick={() => { setSearch(""); setStart(today()); setEnd(today()); setStatus(""); }}>Limpar filtros</button>
    </section>
    <section className="orders-sync glass" style={{padding:20, marginTop:20}}>
      <strong>Conexao com o Tiny</strong>
      <p>Recuperacao automatica de pedidos ausentes a cada 2 minutos, considerando apenas o dia atual. Para outro periodo, preencha as datas acima.</p>
      {sync && <p role="status">{sync.running ? "Buscando pedidos no Tiny..." : sync.message}
        {sync.lastAttemptAt && " Ultima verificacao: " + new Date(sync.lastAttemptAt).toLocaleString("pt-BR", {timeZone:"America/Sao_Paulo"})}</p>}
      {sync?.error && <p role="alert">{sync.error}</p>}
      <button disabled={sync?.running} onClick={synchronize}>{sync?.running ? "Recuperando..." : "Recuperar pedidos do Tiny"}</button>
    </section>
    {invalidPeriod && <p role="alert">A data inicial deve ser anterior ou igual ? data final.</p>}
    {error && <p role="alert">{error}</p>}
    {loading ? <p role="status">Carregando pedidos...</p> : <>
      <p className="orders-count">{count} pedido(s) em {groups.length} data(s).</p>
      {!groups.length && !invalidPeriod && <div className="orders-empty glass">{orders.length ? "Nenhum pedido corresponde aos filtros." : "Nenhum pedido cadastrado no sistema."}</div>}
      {groups.map(([date, rows]) => {
        const total = rows.reduce((sum, order) => sum + Number(parse(order.financialDetails).total ?? parse(order.orderDetails).valorTotalPedido ?? 0), 0);
        return <section className="orders-day glass" key={date}>
          <header><h2>{dateLabel(date)}</h2><span>{rows.length} pedido(s) ? Total informado: {currency.format(total)}</span></header>
          <div className="orders-table"><table><thead><tr>{["Pedido", "Cliente", "Ecommerce", "Status", "Total", "Valor real", "Detalhes"].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>{rows.map(order => <tr key={order.id}>
              <td><strong>#{order.number}</strong><small>ID Tiny: {order.olistOrderId}</small></td>
              <td>{order.customerName || "Sem informa??o"}</td><td>{order.ecommerceName || "Sem informa??o"}</td>
              <td><span className="order-status">{order.statusDescription || order.statusCode}</span></td>
              <td>{money(parse(order.financialDetails).total ?? parse(order.orderDetails).valorTotalPedido)}</td>
              <td>{money(order.realValue)}</td><td><button onClick={() => setSelected(order)}>Ver detalhes</button></td>
            </tr>)}</tbody></table></div>
        </section>;
      })}
    </>}
    {selected && <section className="order-detail glass" aria-label={"Detalhes do pedido " + selected.number}>
      <header><h2>Pedido #{selected.number}</h2><button onClick={() => setSelected(null)}>Fechar detalhes</button></header>
      <p>Data do pedido: {dateLabel(selected.orderDate)} ? Cadastro: {selected.createdAt ? new Date(selected.createdAt).toLocaleString("pt-BR", {timeZone:"America/Sao_Paulo"}) : "Sem informa??o"}</p>
      <h3>Dados completos do pedido</h3><Fields value={parse(selected.orderDetails)} />
      <h3>Composi??o do valor real</h3><Fields value={parse(selected.financialDetails)} />
      <h3>Notifica??o recebida</h3><Fields value={parse(selected.rawPayload)} />
    </section>}
  </main></div>;
}
