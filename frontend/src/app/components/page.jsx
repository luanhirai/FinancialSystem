"use client";

import { useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";

export default function Sidebar() {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState({});  // começa vazio igual ao servidor

  useEffect(() => {
    const stored = localStorage.getItem("user");
    if (stored) setUser(JSON.parse(stored));  // só roda no cliente
  }, []);

  const initials = user.username
    ? user.username.slice(0, 2).toUpperCase()
    : "";

  const navItems = [
    { label: "Dashboard", path: "/dashboard" },
    { label: "Produtos", path: "/products" },
    { label: "Ecommerce", path: "/ecommerce" },
    { label: "Relatórios", path: "/reports" },
    { label: "Configurações", path: "/settings" },
  ];

  const handleLogout = () => {
    localStorage.removeItem("user");
    router.push("/login");
  };

  return (
    <aside className="sidebar glass">
      <div className="sidebar-brand">
        <h2 className="text-gradient">J.A.C.I.R.</h2>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => (
          <button
            key={item.path}
            className={`nav-item ${pathname === item.path ? "active" : ""}`}
            onClick={() => router.push(item.path)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="user-profile">
          <div className="avatar">{initials}</div>
          <div className="user-info">
            <p className="user-name">{user.username || "Usuário"}</p>
            <p className="user-role">{user.email || ""}</p>
          </div>
        </div>
        <button className="logout-button" onClick={handleLogout}>
          Sair
        </button>
      </div>
    </aside>
  );
}