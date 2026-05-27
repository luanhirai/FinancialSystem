"use client";

import { useMemo, useState } from "react";
import Sidebar from "../components/page";
import "./importacao-tiny.css";

const API = "http://localhost:8080";

const authFetch = (url, options = {}) =>
  fetch(url, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

const toInputDate = (date) => date.toISOString().slice(0, 10);

const today = new Date();
const sevenDaysAgo = new Date();
sevenDaysAgo.setDate(today.getDate() - 7);

const statusLabels = {
  0: "Aberto",
  1: "Aprovado",
  2: "Preparando",
  3: "Faturado",
  4: "Pronto envio",
  5: "Enviado",
  6: "Entregue",
  7: "Cancelado",
};

const currency = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const formatCurrency = (value) => {
  const number = Number(value);
  return currency.format(Number.isFinite(number) ? number : 0);
};

const formatDate = (value) => {
  if (!value) return "-";
  const [year, month, day] = value.slice(0, 10).split("-");
  if (!year || !month || !day) return value;
  return `${day}/${month}/${year}`;
};

const getStatusLabel = (status) => statusLabels[status] || `Situacao ${status ?? "-"}`;

export default function ImportacaoTinyPage() {
  const [filters, setFilters] = useState({
    dataInicial: toInputDate(sevenDaysAgo),
    dataFinal: toInputDate(today),
  });
  const [orders, setOrders] = useState([]);
  const [pagination, setPagination] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [importedProducts, setImportedProducts] = useState([]);
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [importing, setImporting] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const periodIsInvalid =
    filters.dataInicial &&
    filters.dataFinal &&
    filters.dataInicial > filters.dataFinal;

  const summary = useMemo(() => {
    const totalValue = orders.reduce((total, order) => total + Number(order.valor || 0), 0);
    const approvedOrders = orders.filter((order) => Number(order.situacao) === 1).length;
    const ecommerces = new Set(
      orders.map((order) => order.ecommerce?.nome).filter(Boolean)
    );

    return {
      totalValue,
      approvedOrders,
      ecommerces: ecommerces.size,
    };
  }, [orders]);

  const updateFilter = (key, value) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const buildPeriodParams = () =>
    new URLSearchParams({
      dataInicial: filters.dataInicial,
      dataFinal: filters.dataFinal,
    }).toString();

  const searchOrders = async () => {
    if (!filters.dataInicial || !filters.dataFinal) {
      setError("Informe a data inicial e a data final.");
      return;
    }

    if (periodIsInvalid) {
      setError("A data inicial deve ser menor ou igual a data final.");
      return;
    }

    setLoadingOrders(true);
    setMessage("");
    setError("");
    setSelectedOrder(null);

    try {
      const res = await authFetch(`${API}/olist/pedidos?${buildPeriodParams()}`);

      if (!res.ok) {
        const msg = await res.text();
        setError(msg || "Nao foi possivel buscar os pedidos do Tiny.");
        return;
      }

      const data = await res.json();
      setOrders(data.itens || []);
      setPagination(data.paginacao || null);
      setMessage(`${data.itens?.length || 0} pedidos encontrados no periodo.`);
    } catch (err) {
      console.error("Erro ao buscar pedidos do Tiny:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setLoadingOrders(false);
    }
  };

  const fetchOrderDetails = async (orderId) => {
    setLoadingDetails(true);
    setMessage("");
    setError("");

    try {
      const res = await authFetch(`${API}/olist/pedidos/${orderId}`);

      if (!res.ok) 
      {
        const msg = await res.text();
        setError(msg || "Nao foi possivel carregar os detalhes do pedido.");
        return;
      }

      setSelectedOrder(await res.json());
    } catch (err) {
      console.error("Erro ao carregar pedido do Tiny:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setLoadingDetails(false);
    }
  };

  const importOrderProducts = async (orderId) => {
    setImporting(true);
    setMessage("");
    setError("");

    try {
      const res = await authFetch(`${API}/olist/pedidos/${orderId}/produtos/importar`, {
        method: "POST",
      });

      if (!res.ok) {
        const msg = await res.text();
        setError(msg || "Nao foi possivel importar os produtos do pedido.");
        return;
      }

      const products = await res.json();
      setImportedProducts(products || []);
      setMessage(`${products?.length || 0} produtos importados do pedido ${orderId}.`);
    } catch (err) {
      console.error("Erro ao importar produtos do pedido:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setImporting(false);
    }
  };

  const importPeriodProducts = async () => {
    if (orders.length === 0) {
      setError("Busque pedidos antes de importar o periodo.");
      return;
    }

    setImporting(true);
    setMessage("");
    setError("");

    try {
      const res = await authFetch(`${API}/olist/pedidos/produtos/importar?${buildPeriodParams()}`, {
        method: "POST",
      });

      if (!res.ok) {
        const msg = await res.text();
        setError(msg || "Nao foi possivel importar os produtos do periodo.");
        return;
      }

      const products = await res.json();
      setImportedProducts(products || []);
      setMessage(`${products?.length || 0} produtos importados dos pedidos do periodo.`);
    } catch (err) {
      console.error("Erro ao importar produtos do periodo:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setImporting(false);
    }
  };

  return (
    <div className="dashboard-layout">
      <Sidebar />

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <span className="eyebrow">Tiny</span>
            <h1>Importacao de dados</h1>
            <p>Busque pedidos por periodo e importe os produtos vendidos para o sistema.</p>
          </div>
          <button
            className="btn-primary"
            type="button"
            onClick={importPeriodProducts}
            disabled={importing || orders.length === 0}
          >
            {importing ? "Importando..." : "Importar periodo"}
          </button>
        </header>

        {message && <p className="success-message">{message}</p>}
        {error && <p className="error-message">{error}</p>}

        <section className="content-section glass animate-fade-in">
          <div className="tiny-period-form">
            <div className="filter-group">
              <label>Data inicial</label>
              <input
                type="date"
                value={filters.dataInicial}
                onChange={(event) => updateFilter("dataInicial", event.target.value)}
              />
            </div>
            <div className="filter-group">
              <label>Data final</label>
              <input
                type="date"
                value={filters.dataFinal}
                onChange={(event) => updateFilter("dataFinal", event.target.value)}
              />
            </div>
            <button
              className="btn-secondary tiny-search-button"
              type="button"
              onClick={searchOrders}
              disabled={loadingOrders}
            >
              {loadingOrders ? "Buscando..." : "Buscar pedidos"}
            </button>
          </div>
        </section>

        <section className="tiny-stats-grid">
          <article className="stat-card glass">
            <p className="stat-label">Pedidos encontrados</p>
            <h3 className="stat-value">{orders.length}</h3>
            <span className="stat-change neutral">
              {pagination?.total ? `${pagination.total} no Tiny` : "Resultado atual"}
            </span>
          </article>
          <article className="stat-card glass">
            <p className="stat-label">Pedidos aprovados</p>
            <h3 className="stat-value">{summary.approvedOrders}</h3>
            <span className="stat-change positive">Prontos para analise</span>
          </article>
          <article className="stat-card glass">
            <p className="stat-label">Valor total</p>
            <h3 className="stat-value">{formatCurrency(summary.totalValue)}</h3>
            <span className="stat-change neutral">Pedidos listados</span>
          </article>
          <article className="stat-card glass">
            <p className="stat-label">Canais</p>
            <h3 className="stat-value">{summary.ecommerces}</h3>
            <span className="stat-change neutral">E-commerces retornados</span>
          </article>
        </section>

        <section className="content-section glass animate-fade-in">
          <div className="section-header">
            <div>
              <h2>Pedidos do Tiny</h2>
              <p>Periodo de {formatDate(filters.dataInicial)} ate {formatDate(filters.dataFinal)}.</p>
            </div>
          </div>

          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Data</th>
                  <th>Canal</th>
                  <th>Situacao</th>
                  <th>Valor</th>
                  <th>Acoes</th>
                </tr>
              </thead>
              <tbody>
                {!loadingOrders && orders.length === 0 && (
                  <tr>
                    <td colSpan="6" className="table-empty">
                      Nenhum pedido carregado para o periodo.
                    </td>
                  </tr>
                )}

                {orders.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <strong>#{order.numeroPedido || order.id}</strong>
                      <span className="tiny-order-id">ID {order.id}</span>
                    </td>
                    <td>{formatDate(order.dataCriacao || order.dataPrevista)}</td>
                    <td>
                      <span className="badge">{order.ecommerce?.nome || "Sem canal"}</span>
                    </td>
                    <td>
                      <span className={`status-pill ${Number(order.situacao) === 7 ? "cancelado" : "recebido"}`}>
                        {getStatusLabel(order.situacao)}
                      </span>
                    </td>
                    <td>{formatCurrency(order.valor)}</td>
                    <td>
                      <div className="action-buttons">
                        <button
                          className="btn-edit"
                          type="button"
                          onClick={() => fetchOrderDetails(order.id)}
                          disabled={loadingDetails}
                        >
                          Detalhes
                        </button>
                        <button
                          className="btn-secondary"
                          type="button"
                          onClick={() => importOrderProducts(order.id)}
                          disabled={importing}
                        >
                          Importar
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {selectedOrder && (
          <section className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>Itens do pedido #{selectedOrder.numeroPedido || selectedOrder.id}</h2>
                <p>{selectedOrder.itens?.length || 0} produtos retornados pelo Tiny.</p>
              </div>
              <button
                className="btn-secondary"
                type="button"
                onClick={() => importOrderProducts(selectedOrder.id)}
                disabled={importing}
              >
                Importar itens
              </button>
            </div>

            <div className="tiny-detail-grid">
              {(selectedOrder.itens || []).map((item) => (
                <article key={`${selectedOrder.id}-${item.produto?.id}`} className="tiny-product-item">
                  <div>
                    <strong>{item.produto?.descricao || "Produto sem descricao"}</strong>
                    <span>SKU {item.produto?.sku || "-"} - ID {item.produto?.id || "-"}</span>
                  </div>
                  <p>{item.quantidade || 0} x {formatCurrency(item.valorUnitario)}</p>
                </article>
              ))}
            </div>
          </section>
        )}

        {importedProducts.length > 0 && (
          <section className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>Produtos importados</h2>
                <p>Ultima importacao executada nesta tela.</p>
              </div>
            </div>

            <div className="tiny-imported-grid">
              {importedProducts.slice(0, 8).map((product) => (
                <article key={product.id || product.id_olist} className="tiny-imported-card">
                  <strong>{product.name}</strong>
                  <span>{product.ecommerce?.name || "Sem ecommerce"}</span>
                  <p>{formatCurrency(product.original_price)} - estoque {product.quantity ?? 0}</p>
                </article>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
