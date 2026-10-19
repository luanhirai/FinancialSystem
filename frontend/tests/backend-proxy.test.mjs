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
