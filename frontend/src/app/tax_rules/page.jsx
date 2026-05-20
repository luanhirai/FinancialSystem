"use client";

import Sidebar from "../components/page";
import "./tax_rules.css";

export default function TaxRulesPage() {
  return (
    <div className="dashboard-layout">
      <Sidebar />

      <main className="main-content">
        <header className="content-header">
          <div className="header-title">
            <h1>Regras fiscais</h1>
            <p>Configure as regras de imposto usadas nos relatorios financeiros.</p>
          </div>
        </header>

        <section className="content-section glass animate-fade-in">
          <div className="empty-state">
            Nenhuma regra fiscal cadastrada ainda.
          </div>
        </section>
      </main>
    </div>
  );
}
