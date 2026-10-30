import test from "node:test";
import assert from "node:assert/strict";
import { GET, POST } from "../src/app/api/backend/[...path]/route.js";

test("proxy rejects host injection, encoded slashes and traversal", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = () => { throw new Error("Unexpected upstream fetch"); };
  try {
    for (const path of [["/evil.example"], [".."], ["%2f%2fevil.example"], ["\\\\evil.example"], ["auth", ".."]]) {
      const response = await GET(new Request("https://app.example/api/backend/test"), { params: Promise.resolve({ path }) });
      assert.equal(response.status, 400);
    }
  } finally { globalThis.fetch = originalFetch; }
});

test("proxy forwards only to configured backend and disables caching", async () => {
  const originalFetch = globalThis.fetch;
  let received;
  globalThis.fetch = async (url, init) => {
    received = { url: String(url), init };
    return new Response("ok", { status: 200 });
  };
  try {
    const response = await GET(new Request("https://app.example/api/backend/auth/me?x=1", {
      headers: { "x-forwarded-host": "evil.example", forwarded: "host=evil.example" },
    }), { params: Promise.resolve({ path: ["auth", "me"] }) });
    assert.equal(received.url, "http://localhost:8080/auth/me?x=1");
    assert.equal(received.init.headers.has("x-forwarded-host"), false);
    assert.equal(received.init.headers.has("forwarded"), false);
    assert.equal(response.headers.get("cache-control"), "no-store");
  } finally { globalThis.fetch = originalFetch; }
});

test("proxy rejects non-JSON bodies and allows bodyless logout", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("ok");
  try {
    const invalid = await POST(new Request("https://app.example/api/backend/auth/login", {
      method: "POST", body: "email=evil", headers: { "content-type": "application/x-www-form-urlencoded" },
    }), { params: Promise.resolve({ path: ["auth", "login"] }) });
    assert.equal(invalid.status, 415);
    const logout = await POST(new Request("https://app.example/api/backend/auth/logout", { method: "POST" }),
      { params: Promise.resolve({ path: ["auth", "logout"] }) });
    assert.equal(logout.status, 200);
  } finally { globalThis.fetch = originalFetch; }
});

test("proxy hides upstream network failure details", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("internal credentials and hostname"); };
  try {
    const response = await GET(new Request("https://app.example/api/backend/auth/me"),
      { params: Promise.resolve({ path: ["auth", "me"] }) });
    assert.equal(response.status, 502);
    assert.equal((await response.text()).includes("credentials"), false);
  } finally { globalThis.fetch = originalFetch; }
});

test("proxy preserves Excel binary content, filters, download headers and authentication", async () => {
  const originalFetch = globalThis.fetch;
  const workbook = new Uint8Array([80, 75, 3, 4, 0, 255, 128, 10]);
  let received;
  globalThis.fetch = async (url, init) => {
    received = { url: String(url), init };
    return new Response(workbook, { headers: {
      "content-type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "content-disposition": "attachment; filename=financeiro.xlsx",
    } });
  };
  try {
    const query = "dataInicial=2026-10-01&dataFinal=2026-10-31&produtoId=11&ecommerce=Loja";
    const response = await GET(new Request("https://app.example/api/backend/olist/relatorios/financeiro/excel?" + query, {
      headers: { cookie: "test_session=private" },
    }), { params: Promise.resolve({ path: ["olist", "relatorios", "financeiro", "excel"] }) });
    assert.equal(received.url, "http://localhost:8080/olist/relatorios/financeiro/excel?" + query);
    assert.equal(received.init.headers.get("cookie"), "test_session=private");
    assert.equal(response.headers.get("content-disposition"), "attachment; filename=financeiro.xlsx");
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.deepEqual(new Uint8Array(await response.arrayBuffer()), workbook);
  } finally { globalThis.fetch = originalFetch; }
});