"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { authFetch } from "@/lib/api";
import Sidebar from "../components/page";
import OrderDetailModal from "../components/OrderDetailModal";
import "../dashboard/dashboard.css";
import "./orders.css";

const today = () => new Intl.DateTimeFormat("en-CA", {timeZone:"America/Sao_Paulo", year:"numeric", month:"2-digit", day:"2-digit"}).format(new Date());
const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const money = value => value == null ? "Pendente" : currency.format(value);
const dateLabel = value => value ? value.split("-").reverse().join("/") : "Sem data";
function parse(value) {
  if (value && typeof value === "object") return value;
  try { return JSON.parse(value) || {}; } catch { return {}; }
}

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState([]);
  const [sync, setSync] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [start, setStart] = useState(today);
  const [end, setEnd] = useState(today);
  const [status, setStatus] = useState("");
  const [dateType, setDateType] = useState("orderDate");
  const [selected, setSelected] = useState(null);
  const [recalculating, setRecalculating] = useState(false);
  const [recalculatingAll, setRecalculatingAll] = useState(false);
  const [bulkMessage, setBulkMessage] = useState("");
  const [bulkError, setBulkError] = useState("");
  const [recalculateError, setRecalculateError] = useState('');
  const [recalculateMessage, setRecalculateMessage] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 15;

  useEffect(() => { setPage(1); }, [search, status, start, end, dateType, pageSize]);



  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      if (start && end && start > end) return;
      const params = new URLSearchParams({dataInicial:start || end || today(), dataFinal:end || start || today(), tipoData:dateType});
      const response = await authFetch("/olist/vendas/pedidos?" + params);
      if (response.status === 401) { router.push("/login"); return; }
      if (!response.ok) throw new Error("Não foi possível carregar os pedidos.");
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

  const exportExcel = async () => {
    if (exporting || (start && end && start > end)) return;
    setExporting(true); setError("");
    try {
      const params = new URLSearchParams({dataInicial:start || end || today(), dataFinal:end || start || today(), tipoData:dateType, busca:search, status});
      const response = await authFetch("/olist/relatorios/financeiro/excel?" + params);
      if (response.status === 401) { router.push("/login"); return; }
      if (!response.ok) throw new Error("Não foi possível exportar os pedidos para Excel.");
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement("a");
      link.href = url; link.download = "pedidos-"+(start || end || today())+"-"+(end || start || today())+".xlsx";
      document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (exception) { setError(exception.message); }
    finally { setExporting(false); }
  };

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

  const recalculate = async id => {
    if (recalculating || recalculatingAll) return;
    setRecalculating(true);
    setRecalculateError("");
    setRecalculateMessage("");
    try {
      const response = await authFetch("/olist/vendas/pedidos/" + id + "/recalcular", { method: "POST" });
      if (response.status === 401) { router.push("/login"); return; }
      if (!response.ok) throw new Error("Não foi possível atualizar os valores do pedido. Tente novamente.");
      const updated = await response.json();
      setOrders(current => current.map(order => order.id === updated.id ? updated : order));
      setSelected(current => current?.id === updated.id ? updated : current);
      setRecalculateMessage("Valores atualizados com os impostos, custos e taxas atuais.");
    } catch (exception) { setRecalculateError(exception.message); }
    finally { setRecalculating(false); }
  };
  const recalculateAll = async () => {
    if (recalculatingAll || recalculating) return;
    setRecalculatingAll(true);
    setBulkMessage("");
    setBulkError("");
    try {
      const response = await authFetch("/olist/vendas/pedidos/recalcular", { method: "POST" });
      if (response.status === 401) { router.push("/login"); return; }
      if (!response.ok) throw new Error("Não foi possível recalcular todos os pedidos. Tente novamente.");
      const result = await response.json();
      setBulkMessage(`${result.total} pedido(s) recalculado(s) com os impostos, custos e taxas atuais. ${result.pending} pedido(s) com valores pendentes.`);
      setSelected(null);
      await load();
    } catch (exception) { setBulkError(exception.message); }
    finally { setRecalculatingAll(false); }
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
  const ecommerceProfits = useMemo(() => {
    const totals = new Map();
    for (const [, rows] of groups) {
      for (const order of rows) {
        const name = order.ecommerceName?.trim() || "Sem ecommerce informado";
        const key = name.toLocaleLowerCase("pt-BR");
        if (!totals.has(key)) totals.set(key, { name, cents: 0, calculated: 0, pending: 0 });
        const total = totals.get(key);
        if (order.realValue == null || String(order.realValue).trim() === "" || !Number.isFinite(Number(order.realValue))) {
          total.pending++;
        } else {
          total.cents += Math.round(Number(order.realValue) * 100);
          total.calculated++;
        }
      }
    }
    return [...totals.values()].sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
  }, [groups]);

  const pageCount = Math.max(1, Math.ceil(count / pageSize));
  const currentPage = Math.min(page, pageCount);
  const offset = (currentPage - 1) * pageSize;
  const visibleGroups = useMemo(() => {
    const result = new Map();
    const rows = groups.flatMap(([date, orders]) => orders.map(order => ({ date, order })));
    for (const { date, order } of rows.slice(offset, offset + pageSize)) {
      if (!result.has(date)) result.set(date, []);
      result.get(date).push(order);
    }
    return [...result.entries()];
  }, [groups, offset, pageSize]);

  useEffect(() => { setPage(value => Math.min(value, pageCount)); }, [pageCount]);

  return <div className="dashboard-layout"><Sidebar /><main className="orders-main">
    <header className="orders-heading"><div><h1>Pedidos</h1>
      <p>Todos os pedidos cadastrados no sistema, agrupados por data. Atualização automática a cada 15 segundos.</p>
    </div><button disabled={refreshing} onClick={load}>{refreshing ? "Atualizando..." : "Atualizar"}</button></header>
    
    <section className="orders-sync glass" style={{padding:20, marginTop:20}}>
      <button type="button" disabled={recalculatingAll || recalculating} data-tour="orders-recalculate" onClick={recalculateAll}>
        {recalculatingAll ? "Recalculando todos os pedidos..." : "Recalcular todos os pedidos"}
      </button>
      <p>Atualiza todos os pedidos da sua conta com os impostos, custos e taxas atuais, independentemente dos filtros e da página.</p>
      {bulkMessage && <p role="status">{bulkMessage}</p>}
      {bulkError && <p role="alert">{bulkError}</p>}
    </section>
    <section className="orders-filters glass" aria-label="Filtros de pedidos">
      <label className="orders-search">Buscar<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Pedido, cliente ou ecommerce" /></label>
      <label>Agrupar por<select value={dateType} onChange={e => setDateType(e.target.value)}><option value="orderDate">Data do pedido</option><option value="createdAt">Data de cadastro</option></select></label>
      <label>De<input type="date" value={start} onChange={e => setStart(e.target.value)} /></label>
      <label>Até<input type="date" value={end} onChange={e => setEnd(e.target.value)} /></label>
      <label>Status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos</option>{statuses.map(s => <option key={s}>{s}</option>)}</select></label>
      <button type="button" disabled={exporting || invalidPeriod} data-tour="orders-export" onClick={exportExcel}>{exporting ? "Exportando..." : "Exportar Excel (.xlsx)"}</button>
      <button onClick={() => { setSearch(""); setStart(today()); setEnd(today()); setStatus(""); }}>Limpar filtros</button>
    </section>

    <section className="orders-sync glass" style={{padding:20, marginTop:20}}>
      <strong data-tour="orders-sync">Conexao com o Tiny</strong>
      <p>Recuperacao automatica de pedidos ausentes a cada 2 minutos, considerando apenas o dia atual. Para outro periodo, preencha as datas acima.</p>
      {sync && <p role="status">{sync.running ? "Buscando pedidos no Tiny..." : sync.message}
        {sync.lastAttemptAt && " Ultima verificacao: " + new Date(sync.lastAttemptAt).toLocaleString("pt-BR", {timeZone:"America/Sao_Paulo"})}</p>}
      {sync?.error && <p role="alert">{sync.error}</p>}
      <button disabled={sync?.running} onClick={synchronize}>{sync?.running ? "Recuperando..." : "Recuperar pedidos do Tiny"}</button>
    </section>

    {invalidPeriod && <p role="alert">A data inicial deve ser anterior ou igual à data final.</p>}
    {error && <p role="alert">{error}</p>}
    {loading ? <p role="status">Carregando pedidos...</p> : <>
      <p className="orders-count">{count} pedido(s) em {groups.length} data(s).</p>
      {ecommerceProfits.length > 0 && <section className="orders-day glass" aria-labelledby="ecommerce-profit-title">
        <header><h2 id="ecommerce-profit-title">Lucro por ecommerce</h2><span>Todos os pedidos dos filtros selecionados</span></header>
        <div className="orders-table"><table>
          <thead><tr><th scope="col">Ecommerce</th><th scope="col">Lucro</th></tr></thead>
          <tbody>{ecommerceProfits.map(item => <tr key={item.name}>
            <td>{item.name}</td>
            <td><strong>{item.calculated ? currency.format(item.cents / 100) : "Pendente"}</strong>
              {item.pending > 0 && <small>{item.calculated ? "Valor parcial · " : ""}{item.pending} pedido(s) com lucro pendente</small>}
            </td>
          </tr>)}</tbody>
        </table></div>
      </section>}
      {!groups.length && !invalidPeriod && <div className="orders-empty glass">{orders.length ? "Nenhum pedido corresponde aos filtros." : "Nenhum pedido cadastrado no sistema."}</div>}
      {visibleGroups.map(([date, rows]) => {
        const total = rows.reduce((sum, order) => sum + Number(parse(order.financialDetails).total ?? parse(order.orderDetails).valorTotalPedido ?? 0), 0);
        return <section className="orders-day glass" key={date}>
          <header><h2>{dateLabel(date)}</h2><span>{rows.length} pedido(s) · Total nesta página: {currency.format(total)}</span></header>
          <div className="orders-table"><table><thead><tr>{["Pedido", "Cliente", "Ecommerce", "Status", "Total", "Valor real", "Detalhes"].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>{rows.map(order => <tr key={order.id}>
              <td><strong>#{order.number}</strong><small>ID Tiny: {order.olistOrderId}</small></td>
              <td>{order.customerName || "Sem informação"}</td><td>{order.ecommerceName || "Sem informação"}</td>
              <td><span className="order-status">{order.statusDescription || order.statusCode}</span></td>
              <td>{money(parse(order.financialDetails).total ?? parse(order.orderDetails).valorTotalPedido)}</td>
              <td>{money(order.realValue)}</td><td><button onClick={() => { setRecalculateError(""); setRecalculateMessage(""); setSelected(order); }}>Ver detalhes</button></td>
            </tr>)}</tbody></table></div>
        </section>;
      })}
    </>}
    {!loading && count > 0 && <nav className="orders-pagination glass" aria-label="Paginação de pedidos">
      <p role="status">Mostrando {offset + 1}–{Math.min(offset + pageSize, count)} de {count} pedidos</p>
      <span>15 pedidos por página</span>
      <div className="orders-page-controls">
        <button type="button" disabled={currentPage === 1} onClick={() => setPage(1)} aria-label="Primeira página">Primeira</button>
        <button type="button" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Anterior</button>
        <span>Página {currentPage} de {pageCount}</span>
        <button type="button" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)}>Próxima</button>
        <button type="button" disabled={currentPage === pageCount} onClick={() => setPage(pageCount)} aria-label="Última página">Última</button>
      </div>
    </nav>}
    {selected && <OrderDetailModal order={selected} onClose={() => setSelected(null)}>
      <button type="button" disabled={recalculating || recalculatingAll} onClick={() => recalculate(selected.id)}>
        {recalculating ? "Atualizando valores..." : "Atualizar valores com impostos atuais"}
      </button>
      <p>Recalcula o valor real com os impostos, custos dos produtos e taxas do ecommerce configurados atualmente.</p>
      {recalculateError && <p role="alert">{recalculateError}</p>}
      {recalculateMessage && <p role="status">{recalculateMessage}</p>}
    </OrderDetailModal>}
  </main></div>;
}
