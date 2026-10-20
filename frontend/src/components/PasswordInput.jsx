"use client";

import { useState } from "react";
import "./password-input.css";

export default function PasswordInput({ id, disabled, visibilityLabel = "senha", ...props }) {
  const [visible, setVisible] = useState(false);
  const action = visible ? "Ocultar" : "Mostrar";

  return (
    <div className="password-input">
      <input {...props} id={id} disabled={disabled} type={visible ? "text" : "password"} className="password-input__field" />
      <button type="button" className="password-input__toggle" disabled={disabled}
        aria-label={`${action} ${visibilityLabel}`} aria-controls={id} aria-pressed={visible}
        onClick={() => setVisible((value) => !value)}>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
          strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          {visible ? <><path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8" />
            <path d="M9.9 5.2A10.5 10.5 0 0 1 12 5c6 0 10 7 10 7a18.3 18.3 0 0 1-3.2 3.9M6.5 6.5A18.2 18.2 0 0 0 2 12s4 7 10 7a11.1 11.1 0 0 0 5.5-1.5" /></> :
            <><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>}
        </svg>
        <span>{action}</span>
      </button>
    </div>
  );
}
