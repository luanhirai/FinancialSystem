"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import "./products.css";
import Sidebar from "../components/page";

const API = "http://localhost:8080";

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
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [importingTiny, setImportingTiny] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [form, setForm] = useState({
    name: "",
    original_price: "",
    cost: "",
    quantity: "",
    id_olist: "",
    ecommerce: { id: "" },
  });

  useEffect(() => {
    fetchProducts();
    fetchEcommerces();
  }, []);

  const authFetch = (url, options = {}) => {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
    return fetch(url, {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      }
    });
  };


  const fetchProducts = async () => {
    try {
      const res = await authFetch(`${API}/product`, { method: "GET" });
      if (!res.ok) return;
      const text = await res.text();
      setProducts(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar produtos:", err);
    }
  };

  const fetchEcommerces = async () => {
    try {
      const res = await authFetch(`${API}/ecommerce`);
      if (!res.ok) return;
      const text = await res.text();
      setEcommerces(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar ecommerces:", err);
    }
  };


  const openCreate = () => {
    setEditingProduct(null);
    setForm({ name: "", original_price: "", cost: "", quantity: "", id_olist: "", ecommerce: { id: "" } });
    setError("");
    setIsModalOpen(true);
  };

  const openEdit = (product) => {
    setEditingProduct(product);
    setForm({
      name: product.name ?? "",
      original_price: product.original_price ?? "",
      cost: product.cost ?? "",
      quantity: product.quantity ?? "",
      id_olist: product.id_olist ?? "",
      ecommerce: { id: product.ecommerce?.id ?? "" },
    });
    setError("");
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setError("");
  };


  const handleSave = async () => {
    if (!form.name.trim()) { 
      setError("Nome é obrigatório."); 
      return; 
    }


    if(form.price < 0 || form.original_price < 0 || form.cost < 0 || form.quantity < 0){
      setError("Os valores devem ser inteiros e maiores que 0");
      return;
    }

    setLoading(true);
    setError("");

    const body = {
      name: form.name,
      original_price: form.original_price ? Number(form.original_price) : null,
      cost: form.cost ? Number(form.cost) : null,
      quantity: form.quantity ? Number(form.quantity) : null,
      id_olist: form.id_olist || null,
      ecommerce: form.ecommerce.id ? { id: Number(form.ecommerce.id) } : null,
    };

    try {
      const url = editingProduct
        ? `${API}/product/update?id=${editingProduct.id}`
        : `${API}/product`;
      const method = editingProduct ? "PUT" : "POST";

      const res = await authFetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        const msg = await res.text();
        setError(msg || "Erro ao salvar produto.");
        return;
      }

      closeModal();
      fetchProducts();
    } catch {
      setError("Erro ao conectar com o servidor.");
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm("Tem certeza que deseja excluir este produto?")) return;
    try {
      const res = await authFetch(`${API}/product/delete?id=${id}`, { method: "DELETE" });
      if (res.ok) fetchProducts();
    } catch (err) {
      console.error("Erro ao deletar produto:", err);
    }
  };

  const handleImportTiny = async () => {
    setImportingTiny(true);
    setImportMessage("");
    setError("");

    try {
      const res = await authFetch(`${API}/produtos/importar-tiny`, {
        method: "POST",
      });

      if (!res.ok) {
        const msg = await res.text();
        setError(msg || "Erro ao importar produtos do Tiny.");
        return;
      }

      const text = await res.text();
      const importedProducts = text ? JSON.parse(text) : [];
      await Promise.all([fetchProducts(), fetchEcommerces()]);
      setImportMessage(`${importedProducts.length} produtos importados do mock Tiny.`);
    } catch (err) {
      console.error("Erro ao importar produtos do Tiny:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setImportingTiny(false);
    }
  };

  const field = (key) => ({
    value: key === "ecommerce" ? form.ecommerce.id : form[key],
    onChange: (e) =>
      key === "ecommerce"
        ? setForm({ ...form, ecommerce: { id: e.target.value } })
        : setForm({ ...form, [key]: e.target.value }),
  });

  const isColumnVisible = (key) => visibleColumns.includes(key);

  const updateFilter = (key, value) => {
    setFilters((currentFilters) => ({ ...currentFilters, [key]: value }));
  };

  const clearFilters = () => {
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
            >
              {importingTiny ? "Importando..." : "Importar Tiny"}
            </button>
            <button className="btn-primary" onClick={openCreate}>+ Novo Produto</button>
          </div>
        </header>

        {importMessage && <p className="success-message">{importMessage}</p>}

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
              <p>{filteredProducts.length} de {products.length} produtos exibidos.</p>
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
                {filteredProducts.map((p) => {
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
                          <button className="btn-edit" onClick={() => openEdit(p)}>Editar</button>
                          <button className="btn-delete" onClick={() => handleDelete(p.id)}>Excluir</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {isModalOpen && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal glass" onClick={(e) => e.stopPropagation()}>
            <h2>{editingProduct ? "Editar Produto" : "Novo Produto"}</h2>

            <div className="form-group">
              <label>Nome</label>
              <input {...field("name")} placeholder="Nome do produto" />
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Preço Original</label>
                <input type="number" {...field("original_price")} placeholder="0.00" />
              </div>
              <div className="form-group">
                <label>Custo</label>
                <input type="number" {...field("cost")} placeholder="0.00" />
              </div>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Quantidade</label>
                <input type="number" {...field("quantity")} placeholder="0" />
              </div>
              <div className="form-group">
                <label>ID Olist</label>
                <input {...field("id_olist")} placeholder="ID externo" />
              </div>
            </div>
            <div className="form-group">
              <label>Ecommerce</label>
              <select {...field("ecommerce")}>
                <option value="">Selecione...</option>
                {ecommerces.map((e) => (
                  <option key={e.id} value={e.id}>{e.name}</option>
                ))}
              </select>
            </div>

            {error && <p className="error-message">{error}</p>}

            <div className="modal-actions">
              <button className="btn-cancel" onClick={closeModal}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave} disabled={loading}>
                {loading ? "Salvando..." : "Salvar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
