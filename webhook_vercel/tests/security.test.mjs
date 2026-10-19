import test from "node:test";
import assert from "node:assert/strict";
process.env.OLIST_WEBHOOK_SECRET = "test-relay-secret";
const { default: handler } = await import("../api/root.js");
function response() {
  return {
    statusCode: 200, headers: {}, value: null,
    setHeader(name, value) { this.headers[name.toLowerCase()] = value; },
    status(value) { this.statusCode = value; return this; },
    json(value) { this.value = value; return this; },
    send(value) { this.value = value; return this; },
    end() { return this; },
  };
}
test("public health endpoint exposes no history or authorization URLs", async () => {
  const res = response();
  await handler({ method: "GET", query: {} }, res);
  assert.equal(res.statusCode, 200);
  assert.deepEqual(Object.keys(res.value).sort(), ["message", "success"]);
  assert.equal(res.headers["cache-control"], "no-store");
});
test("callback never reflects attacker HTML or recovery credentials", async () => {
  const original = globalThis.fetch;
  let upstream;
  globalThis.fetch = async (url, init) => { upstream = init; return Response.json({ message: "ok" }); };
  try {
    const res = response();
    await handler({ method: "GET", query: { code: "<script>evil()</script>", state: "a".repeat(43) } }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.value.includes("<script>"), false);
    assert.equal(res.value.includes("evil()"), false);
    assert.equal(upstream.headers["X-Webhook-Secret"], "test-relay-secret");
    assert.equal(res.headers["content-security-policy"].includes("default-src 'none'"), true);
  } finally { globalThis.fetch = original; }
});
test("callback errors are generic and notifications do not echo customer data", async () => {
  const error = response();
  await handler({ method: "GET", query: { error: "bad", error_description: "<script>evil()</script>" } }, error);
  assert.equal(error.statusCode, 400);
  assert.equal(error.value.includes("<script>"), false);
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response("ok", { status: 200 });
  try {
    const res = response();
    await handler({ method: "POST", query: {}, body: { tipo: "estoque", dados: { idProduto: 1, saldo: 2 }, privateCustomer: "do-not-echo" } }, res);
    assert.equal(res.statusCode, 200);
    assert.equal(JSON.stringify(res.value).includes("do-not-echo"), false);
  } finally { globalThis.fetch = original; }
});
