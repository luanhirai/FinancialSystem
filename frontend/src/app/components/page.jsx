"use client";

import { useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";

export default function Sidebar() {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState({});

  useEffect(() => {
    const stored = localStorage.getItem("user");
    if (stored) setUser(JSON.parse(stored));
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

  const handleLogout = async () => {
    try {
      const user = JSON.parse(localStorage.getItem("user"));
      console.log("Fazendo logout para usuário:", user);
      
      await fetch("http://localhost:8080/auth/logout", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${user?.token}`,
        },
      });
    } catch (err) {
      console.error("Erro ao fazer logout:", err);
    } finally {
      localStorage.removeItem("user");
      router.push("/login");
    }
  };

  return (
    <aside className="sidebar glass">
      <div className="sidebar-brand">
        <h2 className="text-gradient">FINSYS</h2>
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