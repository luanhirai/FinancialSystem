"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import "./login.css";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const router = useRouter();

  const handleSubmit = (e) => {
    e.preventDefault();
    setIsLoading(true);
    
    // Simulating authentication
    setTimeout(() => {
      setIsLoading(false);
      router.push("/dashboard");
    }, 1500);
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

          <button type="submit" className="login-button" disabled={isLoading}>
            {isLoading ? "Entrando..." : "Entrar System"}
          </button>
        </form>

        <div className="login-footer">
          <p>Esqueceu a senha? <span>Recuperar</span></p>
        </div>
      </div>
    </div>
  );
}
