"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "../components/page";

import "./dashboard.css";

const API = "http://localhost:8080";

const currency = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const percent = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const authFetch = (url, options = {}) =>
  fetch(url, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

const toNumber = (value) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

export default function DashboardPage() {
  const [user, setUser] = useState(null);
  const [products, setProducts] = useState([]);
  const [ecommerces, setEcommerces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const router = useRouter();

  useEffect(() => {
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        setError("");

        const [userRes, productsRes, ecommercesRes] = await Promise.all([
          authFetch(`${API}/auth/me`),
          authFetch(`${API}/product`),
          authFetch(`${API}/ecommerce`),
        ]);

        if (userRes.status === 401) {
          router.push("/login");
          return;
        }

        if (!userRes.ok || !productsRes.ok || !ecommercesRes.ok) {
          throw new Error("Nao foi possivel carregar os dados do dashboard.");
        }

        setUser(await userRes.json());
        setProducts(await productsRes.json());
        setEcommerces(await ecommercesRes.json());
      } catch (err) {
        console.error("Erro ao carregar dashboard:", err);
        setError("Nao foi possivel carregar o resumo agora.");
      } finally {
        setLoading(false);
      }
    };

    fetchDashboard();
  }, [router]);

  const summary = useMemo(() => {
    const totals = products.reduce(
      (acc, product) => {
        const price = toNumber(product.original_price);
        const cost = toNumber(product.cost);
        const quantity = toNumber(product.quantity);

        acc.stockValue += price * quantity;
        acc.stockCost += cost * quantity;
        acc.units += quantity;

        if (quantity <= 5) acc.lowStock += 1;
        if (!product.ecommerce?.id) acc.withoutEcommerce += 1;

        return acc;
      },
      {
        stockValue: 0,
        stockCost: 0,
        units: 0,
        lowStock: 0,
        withoutEcommerce: 0,
      }
    );

    const profit = totals.stockValue - totals.stockCost;
    const margin = totals.stockValue ? (profit / totals.stockValue) * 100 : 0;

    return {
      ...totals,
      profit,
      margin,
    };
  }, [products]);

  const ecommerceOverview = useMemo(() => {
    return ecommerces
      .map((ecommerce) => {
        const relatedProducts = products.filter(
          (product) => product.ecommerce?.id === ecommerce.id
        );
        const stockValue = relatedProducts.reduce(
          (total, product) =>
            total +
            toNumber(product.original_price) * toNumber(product.quantity),
          0
        );

        return {
          ...ecommerce,
          products: relatedProducts.length,
          stockValue,
        };
      })
      .sort((a, b) => b.stockValue - a.stockValue)
      .slice(0, 4);
  }, [ecommerces, products]);

  const recentProducts = useMemo(() => {
    return [...products]
      .sort((a, b) => toNumber(b.id) - toNumber(a.id))
      .slice(0, 6);
  }, [products]);

  const importantProducts = useMemo(() => {
    return [...products]
      .filter((product) => toNumber(product.quantity) <= 5)
      .sort((a, b) => toNumber(a.quantity) - toNumber(b.quantity))
      .slice(0, 5);
  }, [products]);

  const stats = [
    {
      label: "E-commerces",
      value: ecommerces.length,
      helper: "plataformas cadastradas",
    },
    {
      label: "Produtos",
      value: products.length,
      helper: `${summary.units} unidades no estoque`,
    },
    {
      label: "Valor em estoque",
      value: currency.format(summary.stockValue),
      helper: "preco x quantidade",
    },
    {
      label: "Lucro potencial",
      value: currency.format(summary.profit),
      helper: `${percent.format(summary.margin)}% de margem media`,
      tone: summary.profit >= 0 ? "positive" : "negative",
    },
  ];

  return (
    <div className="dashboard-layout">
      <Sidebar />

      <main className="main-content">
        <header className="content-header dashboard-hero">
          <div className="header-title">
            <span className="eyebrow">Painel inicial</span>
            <h1>Ola, {user?.name || "usuario"}</h1>
            <p>
              Acompanhe seus e-commerces, produtos, estoque e pontos que
              precisam de atencao.
            </p>
          </div>
          <div className="profile-summary glass">
            <span>Conta</span>
            <strong>{user?.name || "Usuario"}</strong>
            <p>{user?.email || "email nao informado"}</p>
          </div>
        </header>

        {error && <div className="dashboard-alert">{error}</div>}

        <section className="stats-grid">
          {stats.map((stat, index) => (
            <div
              key={stat.label}
              className="stat-card glass animate-fade-in"
              style={{ animationDelay: `${(index + 1) * 0.08}s` }}
            >
              <p className="stat-label">{stat.label}</p>
              <h3 className="stat-value">{loading ? "..." : stat.value}</h3>
              <span className={`stat-change ${stat.tone || "neutral"}`}>
                {stat.helper}
              </span>
            </div>
          ))}
        </section>

        <section className="dashboard-grid">
          <div className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>E-commerces do usuario</h2>
                <p>Resumo por plataforma cadastrada.</p>
              </div>
              <button className="btn-link" onClick={() => router.push("/ecommerce")}>
                Gerenciar
              </button>
            </div>

            <div className="ecommerce-list">
              {!loading && ecommerceOverview.length === 0 && (
                <div className="empty-state">
                  Nenhum ecommerce cadastrado ainda.
                </div>
              )}

              {ecommerceOverview.map((ecommerce) => (
                <article key={ecommerce.id} className="ecommerce-item">
                  <div>
                    <strong>{ecommerce.name}</strong>
                    <span>
                      {ecommerce.products} produtos - taxa {ecommerce.rate ?? 0}%
                    </span>
                  </div>
                  <p>{currency.format(ecommerce.stockValue)}</p>
                </article>
              ))}
            </div>
          </div>

          <div className="content-section glass animate-fade-in">
            <div className="section-header">
              <div>
                <h2>Pontos importantes</h2>
                <p>Itens que merecem verificacao rapida.</p>
              </div>
            </div>

            <div className="insight-list">
              <div className="insight-item warning">
                <strong>{summary.lowStock}</strong>
                <span>produtos com estoque baixo</span>
              </div>
              <div className="insight-item">
                <strong>{summary.withoutEcommerce}</strong>
                <span>produtos sem ecommerce vinculado</span>
              </div>
              <div className="insight-item">
                <strong>{currency.format(summary.stockCost)}</strong>
                <span>custo total estimado</span>
              </div>
            </div>
          </div>
        </section>

        <section className="content-section glass animate-fade-in">
          <div className="section-header">
            <div>
              <h2>Produtos recentes</h2>
              <p>Ultimos produtos cadastrados no sistema.</p>
            </div>
            <button className="btn-link" onClick={() => router.push("/products")}>
              Ver produtos
            </button>
          </div>

          <div className="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Produto</th>
                  <th>Ecommerce</th>
                  <th>Preco</th>
                  <th>Custo</th>
                  <th>Estoque</th>
                  <th>Margem</th>
                </tr>
              </thead>
              <tbody>
                {!loading && recentProducts.length === 0 && (
                  <tr>
                    <td colSpan="6" className="table-empty">
                      Nenhum produto cadastrado ainda.
                    </td>
                  </tr>
                )}

                {recentProducts.map((product) => {
                  const price = toNumber(product.original_price);
                  const cost = toNumber(product.cost);
                  const margin = price ? ((price - cost) / price) * 100 : 0;

                  return (
                    <tr key={product.id}>
                      <td>
                        <strong>{product.name}</strong>
                      </td>
                      <td>
                        <span className="badge">
                          {product.ecommerce?.name || "Sem ecommerce"}
                        </span>
                      </td>
                      <td>{currency.format(price)}</td>
                      <td>{currency.format(cost)}</td>
                      <td>
                        <span
                          className={`stock-pill ${
                            toNumber(product.quantity) <= 5 ? "low" : ""
                          }`}
                        >
                          {product.quantity ?? 0}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`stat-change ${
                            margin >= 0 ? "positive" : "negative"
                          }`}
                        >
                          {percent.format(margin)}%
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>

        {importantProducts.length > 0 && (
          <section className="content-section glass animate-fade-in compact-section">
            <div className="section-header">
              <div>
                <h2>Estoque baixo</h2>
                <p>Produtos com 5 unidades ou menos.</p>
              </div>
            </div>
            <div className="low-stock-grid">
              {importantProducts.map((product) => (
                <article key={product.id} className="low-stock-card">
                  <strong>{product.name}</strong>
                  <span>{product.ecommerce?.name || "Sem ecommerce"}</span>
                  <p>{product.quantity ?? 0} unidades</p>
                </article>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
