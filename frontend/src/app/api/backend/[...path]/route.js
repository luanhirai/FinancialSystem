const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const hopByHopHeaders = new Set([
  "connection", "content-encoding", "content-length", "host", "keep-alive",
  "proxy-authenticate", "proxy-authorization", "te", "trailer", "transfer-encoding",
  "upgrade", "forwarded", "x-forwarded-host",
]);

async function handler(request, context) {
  const { path } = await context.params;
  if (!Array.isArray(path) || path.some(segment =>
    !segment || segment === "." || segment === ".." || /[\\/?#%]/.test(segment))) {
    return Response.json({ message: "Caminho inválido." }, { status: 400 });
  }
  const targetUrl = new URL(BACKEND_URL);
  targetUrl.pathname = "/" + path.map(encodeURIComponent).join("/");
  targetUrl.search = new URL(request.url).search;
  const headers = new Headers(request.headers);
  for (const header of hopByHopHeaders) headers.delete(header);
  const init = { method: request.method, headers, redirect: "manual", cache: "no-store",
    signal: AbortSignal.timeout(30000) };
  if (!["GET", "HEAD"].includes(request.method)) {
    const body = await request.arrayBuffer();
    if (body.byteLength && !request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) {
      return Response.json({ message: "Envie os dados em JSON." }, { status: 415 });
    }
    if (body.byteLength > 1024 * 1024) return new Response(null, { status: 413 });
    init.body = body;
  }
  try {
    const upstream = await fetch(targetUrl, init);
    const responseHeaders = new Headers(upstream.headers);
    for (const header of hopByHopHeaders) responseHeaders.delete(header);
    responseHeaders.set("cache-control", "no-store");
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return Response.json({ message: "Servidor temporariamente indisponível." }, { status: 502 });
  }
}
export const GET = handler;
export const POST = handler;
export const PUT = handler;
export const DELETE = handler;
export const OPTIONS = handler;
