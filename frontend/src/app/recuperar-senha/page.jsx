"use client";

import { useState } from "react";
import Link from "next/link";
import { apiUrl } from "@/lib/api";
import "../login/login.css";

export default function RecoverPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(apiUrl("/auth/forgot-password"), {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim() }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Não foi possível solicitar a recuperação. Tente novamente.");
      setMessage(data.message);
    } catch (err) { setError(err.message || "Não foi possível conectar ao servidor."); }
    finally { setBusy(false); }
  }
  return (
    <main className="login-container">
      <div className="login-background"><div className="blob" /><div className="blob secondary" /></div>
      <div className="login-card glass animate-fade-in">
        <div className="login-header"><h1 className="text-gradient">FINSYS</h1><p>Recuperação de senha</p></div>
        <form className="login-form" onSubmit={submit}>
          <p>Informe o e-mail da sua conta para receber um link de recuperação.</p>
          <div className="form-group"><label htmlFor="email">E-mail</label>
            <input id="email" type="email" autoComplete="email" maxLength={254} required value={email}
              onChange={(event) => setEmail(event.target.value)} placeholder="seu@email.com" />
          </div>
          {error && <div className="error-message" role="alert">{error}</div>}
          {message && <p role="status">{message}</p>}
          <button className="login-button" disabled={busy} type="submit">{busy ? "Enviando..." : "Enviar link de recuperação"}</button>
        </form>
        <div className="login-footer"><Link href="/login">Voltar para o login</Link></div>
      </div>
    </main>
  );
}
