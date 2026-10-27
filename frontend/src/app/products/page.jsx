"use client";

import { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { authFetch } from "@/lib/api";
import "./products.css";
import Sidebar from "../components/page";
import Pagination, { usePagination } from "../components/Pagination";

const productColumns = [
  { key: "name", label: "Nome" },
  { key: "original_price", label: "Preco Original" },
  { key: "cost", label: "Custo" },
  { key: "margin", label: "Margem" },
  { key: "quantity", label: "Quantidade" },
  { key: "id_olist", label: "ID Olist" },
  { key: "ecommerce", label: "Ecommerce" },
];

const defaultVisibleColumns = productColumns.map((column) => column.key);

const valueFilterOptions = [
  { key: "original_price", label: "Preco Original" },
  { key: "cost", label: "Custo" },
  { key: "quantity", label: "Quantidade" },
  { key: "margin", label: "Margem" },
];

export default function ProductsPage() {
  const [products, setProducts] = useState([]);
  const [ecommerces, setEcommerces] = useState([]);
  const [visibleColumns, setVisibleColumns] = useState(defaultVisibleColumns);
  const [filters, setFilters] = useState({
    name: "",
    ecommerceId: "",
    valueField: "original_price",
    minValue: "",
    maxValue: "",
  });
  const [error, setError] = useState("");
  const [importingTiny, setImportingTiny] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [stockStatus, setStockStatus] = useState(null);
  const syncMonitor = useRef(0);

  useEffect(() => {
    fetchProducts();
    fetchEcommerces();
  }, []);

  useEffect(() => {
    const monitorId = ++syncMonitor.current;
    authFetch("/olist/produtos/atualizar-estoque/status")
      .then((response) => response.ok ? response.json() : null)
      .then((status) => {
        if (status?.running && syncMonitor.current === monitorId) {
          return monitorTinyStock(status, monitorId);
        }
      })
      .catch(() => {});
    return () => { ++syncMonitor.current; };
  }, []);

  const fetchProducts = async () => {
    try {
      const res = await authFetch("/product", { method: "GET" });
      if (!res.ok) return;
      const text = await res.text();
      setProducts(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar produtos:", err);
    }
  };

  const fetchEcommerces = async () => {
    try {
      const res = await authFetch("/ecommerce");
      if (!res.ok) return;
      const text = await res.text();
      setEcommerces(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar ecommerces:", err);
    }
  };


  const handleDelete = async (id) => {
    if (!confirm("Tem certeza que deseja excluir este produto?")) return;
    try {
      const res = await authFetch(`/product/delete?id=${id}`, { method: "DELETE" });
      if (res.ok) fetchProducts();
    } catch (err) {
      console.error("Erro ao deletar produto:", err);
    }
  };

  async function monitorTinyStock(initialStatus, monitorId) {
    let status = initialStatus;
    try {
      while (syncMonitor.current === monitorId) {
        setImportingTiny(status.running);
        setStockStatus(status);
        setImportMessage(status.message || "Consultando o Tiny...");
        if (!status.running) {
          if (status.error) setError(status.error);
          await Promise.all([fetchProducts(), fetchEcommerces()]);
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 2000));
        if (syncMonitor.current !== monitorId) return;
        const response = await authFetch("/olist/produtos/atualizar-estoque/status");
        if (!response.ok) throw new Error("Não foi possível consultar o progresso. Clique em Atualizar estoque atual para acompanhar novamente.");
        status = await response.json();
      }
    } catch (err) {
      if (syncMonitor.current === monitorId) setError(err.message || "Não foi possível acompanhar a sincronização.");
    } finally {
      if (syncMonitor.current === monitorId) setImportingTiny(false);
    }
  }

  const handleImportTiny = async () => {
    const monitorId = ++syncMonitor.current;
    setImportingTiny(true);
    setImportMessage("Iniciando atualização do estoque...");
    setStockStatus(null);
    setError("");
    try {
      const response = await authFetch("/olist/produtos/atualizar-estoque", { method: "POST" });
      if (!response.ok) {
        throw new Error(response.status === 429
          ? "Há outras sincronizações em andamento. Tente novamente em alguns minutos."
          : response.status === 401 || response.status === 403
            ? "Sua sessão expirou. Faça login novamente."
            : "Não foi possível iniciar a atualização. Verifique a conexão com o Tiny.");
      }
      if (syncMonitor.current !== monitorId) return;
      await monitorTinyStock(await response.json(), monitorId);
    } catch (err) {
      if (syncMonitor.current === monitorId) {
        setError(err.message || "Não foi possível conectar ao servidor.");
        setImportingTiny(false);
      }
    }
  };

  const isColumnVisible = (key) => visibleColumns.includes(key);

  const updateFilter = (key, value) => {
    productPagination.resetPage();
    setFilters((currentFilters) => ({ ...currentFilters, [key]: value }));
  };

  const clearFilters = () => {
    productPagination.resetPage();
    setFilters({
      name: "",
      ecommerceId: "",
      valueField: "original_price",
      minValue: "",
      maxValue: "",
    });
  };

  const getProductMargin = (product) => {
    const price = Number(product.original_price);
    const cost = Number(product.cost);

    if (!price || Number.isNaN(price) || Number.isNaN(cost)) return null;

    return ((price - cost) / price) * 100;
  };

  const getProductValue = (product, fieldKey) => {
    if (fieldKey === "margin") return getProductMargin(product);

    const value = Number(product[fieldKey]);
    return Number.isNaN(value) ? null : value;
  };

  const filteredProducts = products.filter((product) => {
    const productName = (product.name || "").toLowerCase();
    const searchName = filters.name.trim().toLowerCase();
    const productEcommerceId = product.ecommerce?.id ? String(product.ecommerce.id) : "";
    const value = getProductValue(product, filters.valueField);
    const minValue = filters.minValue === "" ? null : Number(filters.minValue);
    const maxValue = filters.maxValue === "" ? null : Number(filters.maxValue);

    if (searchName && !productName.includes(searchName)) return false;
    if (filters.ecommerceId && productEcommerceId !== filters.ecommerceId) return false;
    if (minValue !== null && (value === null || value < minValue)) return false;
    if (maxValue !== null && (value === null || value > maxValue)) return false;

    return true;
  });

  const productPagination = usePagination(filteredProducts);

  return (
    <div className="dashboard-layout">
      <Sidebar/>

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <h1>Produtos</h1>
            <p>Gerencie os produtos cadastrados no sistema.</p>
          </div>
          <div className="header-actions">
            <button
              className="btn-secondary tiny-import-button"
              type="button"
              onClick={handleImportTiny}
              disabled={importingTiny}
              aria-busy={importingTiny}
            >
              <svg className={`stock-sync-icon ${importingTiny ? "is-spinning" : ""}`} width="18" height="18"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
                strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M20 7v5h-5M4 17v-5h5" />
                <path d="M6.1 7a7 7 0 0 1 11.6-2L20 8M4 16l2.3 3A7 7 0 0 0 17.9 17" />
              </svg>
              {importingTiny ? "Atualizando estoque..." : "Atualizar estoque atual"}
            </button>
          </div>
        </header>

        {error && <p className="error-message" role="alert">{error}</p>}
        {importMessage && (
          <div className="stock-sync-status" role="status" aria-live="polite">
            <p>{importMessage}</p>
            {stockStatus && (
              <div className="stock-sync-counts">
                <span><strong>{stockStatus.updated ?? 0}</strong> atualizados</span>
                <span><strong>{stockStatus.created ?? 0}</strong> cadastrados</span>
                <span className={stockStatus.failed > 0 ? "stock-sync-warning" : ""}>
                  <strong>{stockStatus.failed ?? 0}</strong> com falha
                </span>
              </div>
            )}
            {stockStatus?.running && stockStatus.total > 0 && (
              <progress value={stockStatus.processed} max={stockStatus.total}
                aria-label={`${stockStatus.processed} de ${stockStatus.total} produtos processados`} />
            )}
            {!stockStatus?.running && stockStatus?.failed > 0 && (
              <p className="stock-sync-warning">Produtos com falha não tiveram o estoque alterado. Tente atualizar novamente.</p>
            )}
          </div>
        )}

        <section className="content-section glass animate-fade-in">
          <div className="product-filters">
            <div className="filter-group search-filter">
              <label>Nome do produto</label>
              <input
                value={filters.name}
                onChange={(e) => updateFilter("name", e.target.value)}
                placeholder="Buscar por nome"
              />
            </div>

            <div className="filter-group">
              <label>Ecommerce</label>
              <select value={filters.ecommerceId} onChange={(e) => updateFilter("ecommerceId", e.target.value)}>
                <option value="">Todos</option>
                {ecommerces.map((ecommerce) => (
                  <option key={ecommerce.id} value={ecommerce.id}>{ecommerce.name}</option>
                ))}
              </select>
            </div>

            <div className="filter-group">
              <label>Campo de valor</label>
              <select
                value={filters.valueField}
                onChange={(e) => updateFilter("valueField", e.target.value)}
              >
                {valueFilterOptions.map((option) => (
                  <option key={option.key} value={option.key}>{option.label}</option>
                ))}
              </select>
            </div>

            <div className="filter-group compact-filter">
              <label>Valor min.</label>
              <input
                type="number"
                value={filters.minValue}
                onChange={(e) => updateFilter("minValue", e.target.value)}
                placeholder="0"
              />
            </div>

            <div className="filter-group compact-filter">
              <label>Valor max.</label>
              <input
                type="number"
                value={filters.maxValue}
                onChange={(e) => updateFilter("maxValue", e.target.value)}
                placeholder="999"
              />
            </div>

            <button className="btn-secondary clear-filters-button" type="button" onClick={clearFilters}>
              Limpar filtros
            </button>
          </div>

          <div className="table-toolbar">
            <div>
              <h2>Colunas da tabela</h2>
              <p>{filteredProducts.length} de {products.length} produtos encontrados.</p>
            </div>
          </div>

          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  {isColumnVisible("name") && <th>Nome</th>}
                  {isColumnVisible("original_price") && <th>Preco Original</th>}
                  {isColumnVisible("cost") && <th>Custo</th>}
                  {isColumnVisible("margin") && <th>Margem</th>}
                  {isColumnVisible("quantity") && <th>Quantidade</th>}
                  {isColumnVisible("id_olist") && <th>ID Olist</th>}
                  {isColumnVisible("ecommerce") && <th>Ecommerce</th>}
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.length === 0 && (
                  <tr>
                    <td colSpan={visibleColumns.length + 1} style={{ textAlign: "center", color: "var(--text-muted)", padding: "32px" }}>
                      {products.length === 0 ? "Nenhum produto cadastrado." : "Nenhum produto encontrado com esses filtros."}
                    </td>
                  </tr>
                )}
                {productPagination.items.map((p) => {
                  const margin = getProductMargin(p);
                  return (
                    <tr key={p.id}>
                      {isColumnVisible("name") && <td><strong>{p.name}</strong></td>}
                      {isColumnVisible("original_price") && <td>R$ {Number(p.original_price || 0).toFixed(2)}</td>}
                      {isColumnVisible("cost") && <td>R$ {Number(p.cost || 0).toFixed(2)}</td>}
                      {isColumnVisible("margin") && (
                        <td>
                          {margin !== null && (
                            <span className={`stat-change ${margin >= 0 ? "positive" : "negative"}`}>
                              {margin.toFixed(1)}%
                            </span>
                          )}
                        </td>
                      )}
                      {isColumnVisible("quantity") && <td>{p.quantity ?? "-"}</td>}
                      {isColumnVisible("id_olist") && <td style={{ color: "var(--text-muted)", fontSize: "0.8rem" }}>{p.id_olist || "-"}</td>}
                      {isColumnVisible("ecommerce") && <td><span className="badge">{p.ecommerce?.name || "-"}</span></td>}
                      <td>
                        <div className="action-buttons">
                          <button className="btn-delete" onClick={() => handleDelete(p.id)}>Excluir</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination {...productPagination.controls} label="produtos" />
        </section>
      </main>

    </div>
  );
}
