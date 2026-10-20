"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { apiUrl } from "@/lib/api";
import PasswordInput from "@/components/PasswordInput";
import "../login/login.css";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [ready, setReady] = useState(false);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const tokenRead = useRef(false);
  useEffect(() => {
    if (tokenRead.current) return;
    tokenRead.current = true;
    const value = new URLSearchParams(window.location.hash.slice(1)).get("token") || "";
    setToken(value); setReady(true);
    window.history.replaceState(null, "", window.location.pathname);
  }, []);
  async function submit(event) {
    event.preventDefault(); setError("");
    if (password !== confirm) { setError("As senhas não coincidem."); return; }
    if (new TextEncoder().encode(password).length > 72) { setError("A senha deve ter no máximo 72 bytes."); return; }
    setBusy(true);
    try {
      const response = await fetch(apiUrl("/auth/reset-password"), {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, password }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Não foi possível alterar a senha. Solicite um novo link.");
      setMessage(data.message); setPassword(""); setConfirm(""); setToken("");
    } catch (err) { setError(err.message || "Não foi possível conectar ao servidor."); }
    finally { setBusy(false); }
  }
  return (
    <main className="login-container">
      <div className="login-background"><div className="blob" /><div className="blob secondary" /></div>
      <div className="login-card glass animate-fade-in">
        <div className="login-header"><h1 className="text-gradient">FINSYS</h1><p>Defina sua nova senha</p></div>
        {message ? <p role="status">{message}</p> : ready && !token ?
          <div className="error-message" role="alert">Link inválido. Solicite uma nova recuperação.</div> :
          <form className="login-form" onSubmit={submit}>
            <div className="form-group"><label htmlFor="password">Nova senha (mínimo 8 caracteres)</label>
              <PasswordInput id="password" autoComplete="new-password" required minLength={8} maxLength={72}
                value={password} onChange={(event) => setPassword(event.target.value)} /></div>
            <div className="form-group"><label htmlFor="confirm">Confirme a nova senha</label>
              <PasswordInput id="confirm" visibilityLabel="confirmação da senha" autoComplete="new-password" required minLength={8} maxLength={72}
                value={confirm} onChange={(event) => setConfirm(event.target.value)} /></div>
            {error && <div className="error-message" role="alert">{error}</div>}
            <button className="login-button" type="submit" disabled={busy || !ready}>{busy ? "Salvando..." : "Salvar nova senha"}</button>
          </form>}
        <div className="login-footer"><Link href="/recuperar-senha">Solicitar novo link</Link><br /><Link href="/login">Voltar para o login</Link></div>
      </div>
    </main>
  );
}
