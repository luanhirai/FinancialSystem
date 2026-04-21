"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import "./ecommerce.css";
import Sidebar from "../components/page";

const authFetch = (url, options = {}) => {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${user.token}`,
      ...options.headers,
    }
  });
};

export default function EcommercePage() {
  const [ecommerces, setEcommerces] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingEcommerce, setEditingEcommerce] = useState(null);
  const [form, setForm] = useState({ name: "" });
  const [error, setError] = useState("");
  const router = useRouter();

  useEffect(() => { fetchEcommerces(); }, []);

  const fetchEcommerces = async () => {
    const res = await authFetch("http://localhost:8080/ecommerce");
    const text = await res.text();
    const data = text ? JSON.parse(text) : [];
    setEcommerces(data);
  };

  const openCreate = () => {
    setEditingEcommerce(null);
    setForm({ name: "" });
    setError("");
    setIsModalOpen(true);
  };

  const openEdit = (ecommerce) => {
    setEditingEcommerce(ecommerce);
    setForm({ name: ecommerce.name, rate: ecommerce.rate, fixed_rate: ecommerce.fixed_rate });
    setError("");
    setIsModalOpen(true);
  };

  const handleSave = async () => {
    const url = editingEcommerce
      ? `http://localhost:8080/ecommerce/editEcommerce?id=${editingEcommerce.id}`
      : "http://localhost:8080/ecommerce";
    const method = editingEcommerce ? "PUT" : "POST";

    try {
      const res = await authFetch(url, {
        method,
        body: JSON.stringify(form),
      });

      if (!res.ok) { setError("Erro ao salvar ecommerce."); return; }
      setIsModalOpen(false);
      fetchEcommerces();
    } catch {
      setError("Erro ao conectar com o servidor.");
    }
  };

  const handleDelete = async (id) => {
    const res = await authFetch(`http://localhost:8080/ecommerce/${id}`, { method: "DELETE" });
    if (!res.ok) {
      const msg = await res.text();
      alert(msg);
      return;
    }
    fetchEcommerces();
  };

  return (
    <div className="dashboard-layout">
      <Sidebar />

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <h1>Ecommerces</h1>
            <p>Gerencie as plataformas de ecommerce cadastradas.</p>
          </div>
          <div className="header-actions">
            <button className="btn-primary" onClick={openCreate}>+ Novo Ecommerce</button>
          </div>
        </header>

        <section className="stats-grid">
          <div className="stat-card glass animate-fade-in">
            <p className="stat-label">Total de Ecommerces</p>
            <h3 className="stat-value">{ecommerces.length}</h3>
          </div>
        </section>

        <section className="content-section glass animate-fade-in">
          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Nome</th>
                  <th>Taxa(Percentual)</th>
                  <th>Taxa Fixa</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {ecommerces.map((e) => (
                  <tr key={e.id}>
                    <td style={{ color: "var(--text-muted)" }}>#{e.id}</td>
                    <td><strong>{e.name}</strong></td>
                    <td><strong>{e.rate}</strong></td>
                    <td><strong>{e.fixed_rate}</strong></td>
                    <td>
                      <div className="action-buttons">
                        <button className="btn-edit" onClick={() => openEdit(e)}>Editar</button>
                        <button className="btn-delete" onClick={() => handleDelete(e.id)}>Excluir</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {isModalOpen && (
        <div className="modal-overlay" onClick={() => setIsModalOpen(false)}>
          <div className="modal glass" onClick={(e) => e.stopPropagation()}>
            <h2>{editingEcommerce ? "Editar Ecommerce" : "Novo Ecommerce"}</h2>
            <div className="form-group">
              <label>Nome</label>
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Nome do ecommerce" />
            </div>
            <div className="form-group">
              <label>Taxa percentual</label>
              <input value={form.rate} onChange={(e) => setForm({ ...form, rate: e.target.value })} placeholder="Taxa percentual" />
            </div>
            <div className="form-group">
              <label>Taxa fixa do ecommerce</label>
              <input value={form.fixed_rate} onChange={(e) => setForm({ ...form, fixed_rate: e.target.value })} placeholder="Taxa fixa do ecommerce" />
            </div>
            {error && <p className="error-message">{error}</p>}
            <div className="modal-actions">
              <button className="btn-cancel" onClick={() => setIsModalOpen(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave}>Salvar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}