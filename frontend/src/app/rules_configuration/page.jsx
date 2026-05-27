"use client";

import { useEffect, useState } from "react";
import Sidebar from "../components/page";
import "./rules_configuration.css";

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

export default function RulesConfigurationPage() {
    const [taxPercentage, setTaxPercentage] = useState("");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    useEffect(() => {
        const fetchPolicySetting = async () => {
            try {
                setLoading(true);
                setError("");

                const res = await authFetch(`${API}/policy-settings`);

                if (!res.ok) {
                    const msg = await res.text();
                    setError(msg || "Nao foi possivel carregar a configuracao.");
                    return;
                }

                const data = await res.json();
                setTaxPercentage(data.rate ?? "");
            } catch (err) {
                console.error("Erro ao carregar configuracao de imposto:", err);
                setError("Erro ao conectar com o servidor.");
            } finally {
                setLoading(false);
            }
        };

        fetchPolicySetting();
    }, []);

    const savePolicySetting = async () => {
        const rate = Number(taxPercentage);

        if (taxPercentage === "" || Number.isNaN(rate)) {
            setError("Informe um percentual valido.");
            setMessage("");
            return;
        }

        if (rate < 0) {
            setError("O percentual de imposto nao pode ser negativo.");
            setMessage("");
            return;
        }

        try {
            setSaving(true);
            setError("");
            setMessage("");

            const res = await authFetch(`${API}/policy-settings`, {
                method: "POST",
                body: JSON.stringify({ rate }),
            });

            if (!res.ok) {
                const msg = await res.text();
                setError(msg || "Nao foi possivel salvar a configuracao.");
                return;
            }

            const data = await res.json();
            setTaxPercentage(data.rate ?? "");
            setMessage("Configuracao de imposto salva com sucesso.");
        } catch (err) {
            console.error("Erro ao salvar configuracao de imposto:", err);
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
                        <span className="eyebrow">Configuracoes</span>
                        <h1>Configuracao de impostos</h1>
                        <p>Configure a taxa de imposto cobrada por produto.</p>
                    </div>
                </header>

                <section className="content-section glass animate-fade-in">
                    <div className="section-header">
                        <div>
                            <h2>Percentual de imposto</h2>
                            <p>Informe o valor percentual usado nos calculos dos produtos.</p>
                        </div>
                    </div>

                    <div className="tax-config-field">
                        <div className="filter-group">
                            <label>Imposto (%)</label>
                            <input
                                type="number"
                                min="0"
                                step="0.01"
                                value={taxPercentage}
                                onChange={(event) => setTaxPercentage(event.target.value)}
                                placeholder="Ex: 12.5"
                                disabled={loading || saving}
                            />
                        </div>
                    </div>

                    {message && <p className="success-message">{message}</p>}
                    {error && <p className="error-message">{error}</p>}

                    <div className="tax-config-actions">
                        <button
                            className="btn-primary"
                            type="button"
                            onClick={savePolicySetting}
                            disabled={loading || saving}
                        >
                            {saving ? "Salvando..." : "Salvar configuracao"}
                        </button>
                    </div>
                </section>
            </main>
        </div>
    );
}
