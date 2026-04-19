"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import "./register.css";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const router = useRouter();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    try {
      const response = await fetch("http://localhost:8080/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, username, phone, password }),
      });

      const message = await response.text();

      if (!response.ok) {
        setError(message); // "Senha incorreta." ou "Usuário não encontrado."
        return;
      }

      router.push("/login");

    } catch (err) {
      setError("Não foi possível conectar ao servidor.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-background">
        <div className="blob"></div>
        <div className="blob secondary"></div>
      </div>
      
      <div className="login-card glass animate-fade-in">
        <div className="login-header">
          <h1 className="text-gradient">J.A.C.I.R.</h1>
          <p>Sistema de Relatório Financeiro</p>
        </div>
        <div className="login-header">
          <p>Faça seu registro</p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="email">E-mail</label>
            <input 
              id="email"
              type="email" 
              placeholder="seu@email.com" 
              required 
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="username">Nome de Usuário</label>
            <input 
              id="username"
              type="text" 
              placeholder="seu nome de usuário" 
              required 
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="phone">Telefone</label>
            <input 
              id="phone"
              type="tel" 
              placeholder="seu telefone" 
              required 
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Senha</label>
            <input 
              id="password"
              type="password" 
              placeholder="••••••••" 
              required 
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {/* Mensagem de erro */}
          {error && (
            <div className="error-message">
              {error}
            </div>
          )}

          <button type="submit" className="login-button" disabled={isLoading}>
            {isLoading ? "Entrando..." : "Entrar System"}
          </button>
        </form>

        <div className="login-footer">
          <p>Já tem uma conta? <span onClick={() => router.push("/login")} style={{ cursor: "pointer" }}>Entrar</span></p>
        </div>
      </div>
    </div>
  );
}