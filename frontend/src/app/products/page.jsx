"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import "./products.css";

const API = "http://localhost:8080";

export default function ProductsPage() {
  const [products, setProducts] = useState([]);
  const [ecommerces, setEcommerces] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({
    name: "",
    original_price: "",
    cost: "",
    quantity: "",
    id_olist: "",
    ecommerce: { id: "" },
  });
  const router = useRouter();

  useEffect(() => {
    fetchProducts();
    fetchEcommerces();
  }, []);

  // ─── Fetches ───────────────────────────────────────────────

  const fetchProducts = async () => {
    try {
      const res = await fetch(`${API}/product`, { method: "GET" });
      if (!res.ok) return;
      const text = await res.text();
      setProducts(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar produtos:", err);
    }
  };

  const fetchEcommerces = async () => {
    try {
      const res = await fetch(`${API}/ecommerce`);
      if (!res.ok) return;
      const text = await res.text();
      setEcommerces(text ? JSON.parse(text) : []);
    } catch (err) {
      console.error("Erro ao buscar ecommerces:", err);
    }
  };

  // ─── Modal ─────────────────────────────────────────────────

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

  // ─── CRUD ──────────────────────────────────────────────────

  const handleSave = async () => {
    if (!form.name.trim()) { setError("Nome é obrigatório."); return; }

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

      const res = await fetch(url, {
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
      const res = await fetch(`${API}/product/delete?id=${id}`, { method: "DELETE" });
      if (res.ok) fetchProducts();
    } catch (err) {
      console.error("Erro ao deletar produto:", err);
    }
  };

  const field = (key) => ({
    value: key === "ecommerce" ? form.ecommerce.id : form[key],
    onChange: (e) =>
      key === "ecommerce"
        ? setForm({ ...form, ecommerce: { id: e.target.value } })
        : setForm({ ...form, [key]: e.target.value }),
  });

  // ─── Render ────────────────────────────────────────────────

  return (
    <div className="dashboard-layout">
      <aside className="sidebar glass">
        <div className="sidebar-brand">
          <h2 className="text-gradient">J.A.C.I.R.</h2>
        </div>
        <nav className="sidebar-nav">
          <button className="nav-item" onClick={() => router.push("/dashboard")}>Dashboard</button>
          <button className="nav-item active">Produtos</button>
          <button className="nav-item" onClick={() => router.push("/ecommerce")}>Ecommerce</button>
        </nav>
        <div className="sidebar-footer">
          <div className="user-profile">
            <div className="avatar">JD</div>
            <div className="user-info">
              <p className="user-name">João D'Agostini</p>
              <p className="user-role">Finance Manager</p>
            </div>
          </div>
          <button className="logout-button" onClick={() => { localStorage.removeItem("user"); router.push("/login"); }}>
            Sair
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <h1>Produtos</h1>
            <p>Gerencie os produtos cadastrados no sistema.</p>
          </div>
          <div className="header-actions">
            <button className="btn-primary" onClick={openCreate}>+ Novo Produto</button>
          </div>
        </header>

        <section className="content-section glass animate-fade-in">
          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Preço Original</th>
                  <th>Custo</th>
                  <th>Margem</th>
                  <th>Quantidade</th>
                  <th>ID Olist</th>
                  <th>Ecommerce</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {products.length === 0 && (
                  <tr>
                    <td colSpan={8} style={{ textAlign: "center", color: "var(--text-muted)", padding: "32px" }}>
                      Nenhum produto cadastrado.
                    </td>
                  </tr>
                )}
                {products.map((p) => {
                  const margin = p.original_price && p.cost
                    ? (((p.original_price - p.cost) / p.original_price) * 100).toFixed(1)
                    : null;
                  return (
                    <tr key={p.id}>
                      <td><strong>{p.name}</strong></td>
                      <td>R$ {Number(p.original_price || 0).toFixed(2)}</td>
                      <td>R$ {Number(p.cost || 0).toFixed(2)}</td>
                      <td>
                        {margin !== null && (
                          <span className={`stat-change ${margin >= 0 ? "positive" : "negative"}`}>
                            {margin}%
                          </span>
                        )}
                      </td>
                      <td>{p.quantity ?? "—"}</td>
                      <td style={{ color: "var(--text-muted)", fontSize: "0.8rem" }}>{p.id_olist || "—"}</td>
                      <td><span className="badge">{p.ecommerce?.name || "—"}</span></td>
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