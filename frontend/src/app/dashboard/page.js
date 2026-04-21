"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "../components/page";

import "./dashboard.css";

export default function DashboardPage() {
  const [activeTab, setActiveTab] = useState("overview");
  const [user, setUser] = useState(null);
  const router = useRouter();

  const handleLogout = async () => {
    await fetch("http://localhost:8080/auth/logout", { method: "POST" });
    localStorage.removeItem("user");
    router.push("/login");
  };

  const financialData = [
    { id: 1, label: "Saldo Total", value: "R$ 124.500,00", change: "+12%", type: "balance" },
    { id: 2, label: "Receitas", value: "R$ 45.200,00", change: "+5%", type: "income" },
    { id: 3, label: "Despesas", value: "R$ 12.800,00", change: "-2%", type: "expense" },
    { id: 4, label: "Investimentos", value: "R$ 82.100,00", change: "+18%", type: "invest" },
  ];

  const transactions = [
    { id: 1, company: "Google Cloud", date: "16 Abr 2026", amount: "-R$ 1.200,00", status: "Pago", category: "Infraestrutura" },
    { id: 2, company: "Apple Payment", date: "15 Abr 2026", amount: "-R$ 850,00", status: "Processando", category: "Software" },
    { id: 3, company: "Stripe Billing", date: "14 Abr 2026", amount: "+R$ 12.400,00", status: "Recebido", category: "Vendas" },
    { id: 4, company: "AWS Services", date: "12 Abr 2026", amount: "-R$ 2.100,00", status: "Pago", category: "Infraestrutura" },
    { id: 5, company: "Figma Pro", date: "10 Abr 2026", amount: "-R$ 150,00", status: "Pago", category: "Design" },
  ];



  useEffect(() => {
    const user = JSON.parse(localStorage.getItem("user"));
    setUser(user);
    // user.id, user.email, user.username, user.phone
  }, []);


  useEffect(() => {
    const user = localStorage.getItem("user");
    if (!user) {
      router.push("/login");
    }
  }, [router]);

  return (
    <div className="dashboard-layout">
      <Sidebar />
      {/* Main Content */}
      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <h1>Visão Geral</h1>
            <p>Bem-vindo de volta, {user?.username}. Veja o resumo do seu sistema financeiro.</p>
          </div>
          <div className="header-actions">
            <button className="btn-primary">Gerar Relatório</button>
          </div>
        </header>

        <section className="stats-grid">
          {financialData.map((stat) => (
            <div key={stat.id} className="stat-card glass animate-fade-in" style={{ animationDelay: `${stat.id * 0.1}s` }}>
              <p className="stat-label">{stat.label}</p>
              <h3 className="stat-value">{stat.value}</h3>
              <span className={`stat-change ${stat.change.startsWith("+") ? "positive" : "negative"}`}>
                {stat.change} este mês
              </span>
            </div>
          ))}
        </section>

        <section className="content-section glass animate-fade-in" style={{ animationDelay: "0.5s" }}>
          <div className="section-header">
            <h2>Transações Recentes</h2>
            <button className="btn-link">Ver tudo</button>
          </div>
          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Empresa / Cliente</th>
                  <th>Data</th>
                  <th>Valor</th>
                  <th>Categoria</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((t) => (
                  <tr key={t.id}>
                    <td><strong>{t.company}</strong></td>
                    <td>{t.date}</td>
                    <td className={t.amount.startsWith("+") ? "text-income" : ""}>{t.amount}</td>
                    <td><span className="badge">{t.category}</span></td>
                    <td><span className={`status-pill ${t.status.toLowerCase()}`}>{t.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}