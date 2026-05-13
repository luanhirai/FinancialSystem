"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

export default function Sidebar() {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState({});

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await fetch("http://localhost:8080/auth/me", {
          credentials: "include",
        });

        if (!res.ok) {
          router.push("/login");
          return;
        }

        const data = await res.json();
        setUser(data);
      } catch {
        router.push("/login");
      }
    };

    fetchUser();
  }, [router]);

  const displayName = user.name || user.username || "Usuario";
  const displayEmail = user.email || "";
  const initials = displayName.slice(0, 2).toUpperCase();

  const navItems = [
    { label: "Dashboard", path: "/dashboard" },
    { label: "Produtos", path: "/products" },
    { label: "Ecommerce", path: "/ecommerce" },
    { label: "Relatorios", path: "/reports" },
    { label: "Configuracoes", path: "/settings" },
  ];

  const handleLogout = async () => {
    try {
      await fetch("http://localhost:8080/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch (err) {
      console.error("Erro ao fazer logout:", err);
    } finally {
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
            <p className="user-name">{displayName}</p>
            <p className="user-role">{displayEmail}</p>
          </div>
        </div>
        <button className="logout-button" onClick={handleLogout}>
          Sair
        </button>
      </div>
    </aside>
  );
}
