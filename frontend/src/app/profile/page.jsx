"use client";

import { useEffect, useState } from "react";
import Sidebar from "../components/page";
import "./profile.css";

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

const emptyForm = {
  name: "",
  email: "",
  phone: "",
  clientId: "",
  clientSecret: "",
};

const maskValue = (value, visible = 8) => {
  if (!value) {
    return "";
  }

  const text = String(value);
  return text.length <= visible ? "[informado]" : `${text.slice(0, visible)}...`;
};

export default function ProfilePage() {
  const [form, setForm] = useState(emptyForm);
  const [hasClientSecret, setHasClientSecret] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        setLoading(true);
        setError("");

        const res = await authFetch(`${API}/auth/me`);

        if (!res.ok) {
          const msg = await res.text();
          setError(msg || "Nao foi possivel carregar seu perfil.");
          return;
        }

        const data = await res.json();
        setForm({
          name: data.name || "",
          email: data.email || "",
          phone: data.phone || "",
          clientId: data.clientId || "",
          clientSecret: "",
        });
        setHasClientSecret(Boolean(data.hasClientSecret));
      } catch (err) {
        console.error("Erro ao carregar perfil:", err);
        setError("Erro ao conectar com o servidor.");
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, []);

  const updateField = (field, value) => {
    setForm((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const buildProfilePayload = () => {
    const payload = {
      name: form.name,
      email: form.email,
      phone: form.phone,
      clientId: form.clientId,
    };

    if (form.clientSecret.trim()) {
      payload.clientSecret = form.clientSecret;
    }

    console.log("[Olist/Tiny] Payload do perfil montado", {
      hasName: Boolean(payload.name),
      hasEmail: Boolean(payload.email),
      hasPhone: Boolean(payload.phone),
      clientId: maskValue(payload.clientId, 12),
      clientSecret: payload.clientSecret ? "[informado]" : "[nao enviado]",
    });

    return payload;
  };

  const saveProfileData = async () => {
    console.log("[Olist/Tiny] Salvando perfil antes de conectar", {
      endpoint: `${API}/auth/me`,
      hasClientId: Boolean(form.clientId),
      hasNewClientSecret: Boolean(form.clientSecret),
      hasSavedClientSecret: hasClientSecret,
    });

    const res = await authFetch(`${API}/auth/me`, {
      method: "PUT",
      body: JSON.stringify(buildProfilePayload()),
    });

    console.log("[Olist/Tiny] Resposta ao salvar perfil", {
      status: res.status,
      ok: res.ok,
    });

    if (!res.ok) {
      const msg = await res.text();
      console.error("[Olist/Tiny] Backend recusou salvar perfil", msg);
      throw new Error(msg || "Nao foi possivel salvar seu perfil.");
    }

    const data = await res.json();
    console.log("[Olist/Tiny] Perfil salvo com sucesso", {
      userId: data.id,
      hasClientId: Boolean(data.clientId),
      hasClientSecret: Boolean(data.hasClientSecret),
    });

    return data;
  };

  const handleConnectOlistTiny = async () => {
    console.log("[Olist/Tiny] Clique no botao conectar", {
      clientId: maskValue(form.clientId, 12),
      hasNewClientSecret: Boolean(form.clientSecret),
      hasSavedClientSecret: hasClientSecret,
    });

    if (!form.clientId || (!form.clientSecret && !hasClientSecret)) {
      console.warn("[Olist/Tiny] Conexao bloqueada: faltam credenciais");
      setError("Informe os dados da sua conta Olist para conectar.");
      return;
    }

    const authorizationWindow = window.open("about:blank", "_blank");

    if (!authorizationWindow) {
      console.warn("[Olist/Tiny] Navegador bloqueou a nova aba de autorizacao");
      setError("O navegador bloqueou a nova aba. Permita pop-ups para conectar com Olist/Tiny.");
      return;
    }

    authorizationWindow.opener = null;
    authorizationWindow.document.write("<p>Preparando conexao com Olist/Tiny...</p>");

    try {
      setSaving(true);
      setMessage("");
      setError("");

      await saveProfileData();
      console.log("[Olist/Tiny] Credenciais salvas, montando URL de autorizacao");

      const params = new URLSearchParams({
        response_type: "code",
        client_id: form.clientId.trim(),
        redirect_uri: "http://webhook.casalamavievendas.com.br/",
        state: form.clientId.trim(),
      });

      const authorizationUrl = `https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/auth?${params.toString()}`;
      console.log("[Olist/Tiny] Abrindo autorizacao em nova aba", {
        authHost: "accounts.tiny.com.br",
        redirectUri: "http://webhook.casalamavievendas.com.br/",
        state: maskValue(form.clientId, 12),
      });

      authorizationWindow.location.href = authorizationUrl;
    } catch (err) {
      console.error("Erro ao conectar com Olist/Tiny:", err);
      authorizationWindow.close();
      setError(err.message || "Erro ao conectar com o servidor.");
      setSaving(false);
    }
  };

  const saveProfile = async (event) => {
    event.preventDefault();
    setSaving(true);
    setMessage("");
    setError("");

    try {
      const data = await saveProfileData();
      setForm({
        name: data.name || "",
        email: data.email || "",
        phone: data.phone || "",
        clientId: data.clientId || "",
        clientSecret: "",
      });
      setHasClientSecret(Boolean(data.hasClientSecret));
      setMessage("Perfil atualizado com sucesso.");
    } catch (err) {
      console.error("Erro ao salvar perfil:", err);
      setError("Erro ao conectar com o servidor.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="dashboard-layout">
      <Sidebar />

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <span className="eyebrow">Perfil</span>
            <h1>Dados do usuario</h1>
            <p>Atualize suas informacoes pessoais e as credenciais da Olist/Tiny.</p>
          </div>
          <div className="profile-summary glass">
            <span>Credenciais</span>
            <strong>{form.clientId ? "Client ID informado" : "Client ID pendente"}</strong>
            <p>{hasClientSecret ? "Client secret salvo" : "Client secret nao salvo"}</p>
          </div>
        </header>

        <form className="profile-grid" onSubmit={saveProfile}>
          <section className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>Informacoes pessoais</h2>
                <p>Esses dados identificam sua conta no sistema.</p>
              </div>
            </div>

            <div className="profile-form-grid">
              <div className="filter-group">
                <label htmlFor="name">Nome completo</label>
                <input
                  id="name"
                  type="text"
                  value={form.name}
                  onChange={(event) => updateField("name", event.target.value)}
                  placeholder="Seu nome"
                  disabled={loading || saving}
                  required
                />
              </div>

              <div className="filter-group">
                <label htmlFor="email">E-mail</label>
                <input
                  id="email"
                  type="email"
                  value={form.email}
                  onChange={(event) => updateField("email", event.target.value)}
                  placeholder="seu@email.com"
                  disabled={loading || saving}
                  required
                />
              </div>

              <div className="filter-group">
                <label htmlFor="phone">Telefone</label>
                <input
                  id="phone"
                  type="tel"
                  value={form.phone}
                  onChange={(event) => updateField("phone", event.target.value)}
                  placeholder="(00) 00000-0000"
                  disabled={loading || saving}
                  required
                />
              </div>
            </div>
          </section>

          <section className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>Credenciais Olist/Tiny</h2>
                <p>Use os dados do aplicativo para gerar e renovar o token da integracao.</p>
              </div>
              <span className={`credential-status ${hasClientSecret ? "ok" : "pending"}`}>
                {hasClientSecret ? "Secret salvo" : "Secret pendente"}
              </span>
            </div>

            <div className="profile-form-grid">
              <div className="filter-group">
                <label htmlFor="clientId">Client ID</label>
                <input
                  id="clientId"
                  type="text"
                  value={form.clientId}
                  onChange={(event) => updateField("clientId", event.target.value)}
                  placeholder="Informe o client_id"
                  disabled={loading || saving}
                />
              </div>

              <div className="filter-group">
                <label htmlFor="clientSecret">Client Secret</label>
                <input
                  id="clientSecret"
                  type="password"
                  value={form.clientSecret}
                  onChange={(event) => updateField("clientSecret", event.target.value)}
                  placeholder={hasClientSecret ? "Preencha apenas para alterar" : "Informe o client_secret"}
                  disabled={loading || saving}
                  autoComplete="new-password"
                />
              </div>
            </div>
          </section>

          {(message || error) && (
            <div className="profile-feedback">
              {message && <p className="success-message">{message}</p>}
              {error && <p className="error-message">{error}</p>}
            </div>
          )}
          <div className="profile-actions">
            <button className="btn-primary" type="button" onClick={handleConnectOlistTiny} disabled={loading || saving}>
              Conectar com Olist/Tiny
            </button>
          </div>

          <div className="profile-actions">
            <button className="btn-primary" type="submit" disabled={loading || saving}>
              {saving ? "Salvando..." : "Salvar perfil"}
            </button>
          </div>
        </form>
      </main>
    </div>
  );
}
