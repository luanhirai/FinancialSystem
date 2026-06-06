export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  "/api/backend";

export function apiUrl(path) {
  return `${API_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

export function authFetch(path, options = {}) {
  const url = path.startsWith("http") ? path : apiUrl(path);

  return fetch(url, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });
}
