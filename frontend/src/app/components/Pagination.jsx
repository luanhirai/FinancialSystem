"use client";

import { useEffect, useState } from "react";
import styles from "./Pagination.module.css";

export function usePagination(items) {
  const [requestedPage, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const page = Math.min(requestedPage, totalPages);
  const start = (page - 1) * pageSize;

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  return {
    items: items.slice(start, start + pageSize),
    resetPage: () => setPage(1),
    controls: {
      page,
      pageSize,
      totalPages,
      total: items.length,
      onPageChange: (value) => setPage(Math.max(1, Math.min(value, totalPages))),
      onPageSizeChange: (value) => {
        setPageSize(value);
        setPage(1);
      },
    },
  };
}

export default function Pagination({
  page, pageSize, totalPages, total, onPageChange, onPageSizeChange,
  label = "registros", disabled = false,
}) {
  const first = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const last = Math.min(page * pageSize, total);

  return (
    <nav className={styles.pagination} aria-label={`Paginacao de ${label}`}>
      <span className={styles.summary} role="status">
        {first}–{last} de {total} {label}
      </span>
      <label className={styles.size}>
        Por página
        <select value={pageSize} disabled={disabled}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}>
          {[10, 25, 50].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <div className={styles.buttons}>
        <button type="button" disabled={disabled || page === 1} onClick={() => onPageChange(1)} aria-label="Primeira pagina">«</button>
        <button type="button" disabled={disabled || page === 1} onClick={() => onPageChange(page - 1)}>Anterior</button>
        <span aria-live="polite">Página {page} de {totalPages}</span>
        <button type="button" disabled={disabled || page === totalPages} onClick={() => onPageChange(page + 1)}>Próxima</button>
        <button type="button" disabled={disabled || page === totalPages} onClick={() => onPageChange(totalPages)} aria-label="Ultima pagina">»</button>
      </div>
    </nav>
  );
}