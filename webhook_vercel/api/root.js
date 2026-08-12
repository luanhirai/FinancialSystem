const historico = {
  callbacks: [],
  webhooks: [],
};

const TINY_CLIENT_ID =
  process.env.TINY_CLIENT_ID ||
  "tiny-api-479441b5ef29d2e212c3f522ad135840cbfccf54-1779109441";
const TINY_REDIRECT_URI =
  process.env.TINY_REDIRECT_URI || "http://webhook.casalamavievendas.com.br/";
const BACKEND_URL =
  process.env.BACKEND_URL || "https://faqs-combo-went-teaching.trycloudflare.com";

const TINY_AUTH_URL =
  process.env.TINY_AUTH_URL ||
  "https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/auth";

function configurarCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

function montarUrlLogin(clientId = TINY_CLIENT_ID) {
  if (!clientId) return null;

  const params = new URLSearchParams({
    response_type: "code",
    client_id: clientId,
    redirect_uri: TINY_REDIRECT_URI,
    state: clientId,
  });

  return `${TINY_AUTH_URL}?${params.toString()}`;
}

function primeiroValor(valor) {
  if (Array.isArray(valor)) {
    return valor[0] || null;
  }

  return valor || null;
}

function normalizarCallback({ code, state, clientId, error, errorDescription, payload }) {
  const codeNormalizado = primeiroValor(code);
  const stateNormalizado = primeiroValor(state || clientId || TINY_CLIENT_ID);

  return {
    code: codeNormalizado ? String(codeNormalizado).trim() : null,
    state: stateNormalizado ? String(stateNormalizado).trim() : null,
    error: primeiroValor(error),
    errorDescription: primeiroValor(errorDescription),
    payload,
  };
}

function paginaCallback(callback) {
  const sucesso = callback.code && !callback.error;
  const tokenGerado = sucesso && callback.backend?.success;
  const titulo = tokenGerado ? "Conta conectada" : sucesso ? "Code capturado" : "Erro no callback";
  const detalhe = tokenGerado
    ? "O code foi recebido pelo webhook e enviado para o backend."
    : sucesso
    ? callback.backend?.message || "O code foi recebido pelo webhook, mas o backend nao confirmou a troca do token."
    : callback.errorDescription || callback.error || "O Tiny retornou um erro.";

  return `<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${titulo}</title>
    <style>
      body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: Arial, sans-serif; background: #f6f7f9; color: #1f2937; }
      main { width: min(760px, calc(100% - 32px)); background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 24px; }
      code { overflow-wrap: anywhere; background: #f3f4f6; border-radius: 6px; padding: 2px 6px; }
    </style>
  </head>
  <body>
    <main>
      <h1>${titulo}</h1>
      <p>${detalhe}</p>
      <p>Callback: <code>${callback.id}</code></p>
      ${callback.code ? `<p>Code: <code>${callback.code}</code></p>` : ""}
      ${callback.state ? `<p>Client ID: <code>${callback.state}</code></p>` : ""}
      ${callback.backend?.backendEndpoint ? `<p>Backend chamado: <code>${callback.backend.backendEndpoint}</code></p>` : ""}
      ${callback.backend?.status ? `<p>Status backend: <code>${callback.backend.status}</code></p>` : ""}
      ${callback.backend?.data ? `<p>Resposta backend: <code>${typeof callback.backend.data === "string" ? callback.backend.data : JSON.stringify(callback.backend.data)}</code></p>` : ""}
    </main>
  </body>
</html>`;
}

async function enviarCodeParaBackend(callback) {
  const backendEndpoint = `${BACKEND_URL}/auth/olist-token/callback`;

  if (!callback.code) {
    console.warn("[Webhook Olist/Tiny] Callback sem code", {
      callbackId: callback.id,
      hasCode: Boolean(callback.code),
      hasState: Boolean(callback.state),
    });

    return {
      success: false,
      backendEndpoint,
      message: "Callback sem code para enviar ao backend.",
    };
  }

  if (!callback.state) {
    console.warn("[Webhook Olist/Tiny] Callback sem state/client_id mesmo apos fallback", {
      callbackId: callback.id,
      hasDefaultClientId: Boolean(TINY_CLIENT_ID),
    });

    return {
      success: false,
      backendEndpoint,
      message: "Callback sem state/client_id para localizar o usuario.",
    };
  }

  console.log("============================================================");
  console.log("[Webhook Olist/Tiny] VOU PASSAR O CODE DO WEBHOOK PARA O BACKEND.");
  console.log("[Webhook Olist/Tiny] O code veio da URL/corpo recebido pelo webhook.");
  console.log("[Webhook Olist/Tiny] Se o Tiny nao enviou state, estou usando TINY_CLIENT_ID como fallback.");
  console.log("[Webhook Olist/Tiny] Endpoint backend:", backendEndpoint);
  console.log("[Webhook Olist/Tiny] Client ID/state:", callback.state);
  console.log("[Webhook Olist/Tiny] Code recebido?", Boolean(callback.code));
  console.log("[Webhook Olist/Tiny] Prefixo do code:", `${String(callback.code).slice(0, 8)}...`);

  console.log("[Webhook Olist/Tiny] Enviando code para o backend", {
    callbackId: callback.id,
    backendEndpoint,
    clientId: callback.state,
    codePrefix: `${String(callback.code).slice(0, 8)}...`,
  });

  const response = await fetch(backendEndpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      code: callback.code,
      clientId: callback.state,
    }),
  });

  const text = await response.text();
  let data = text;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  console.log("[Webhook Olist/Tiny] Backend respondeu troca do code", {
    callbackId: callback.id,
    status: response.status,
    ok: response.ok,
    data,
  });
  console.log("[Webhook Olist/Tiny] Terminou a chamada do webhook para o backend.");
  console.log("============================================================");

  return {
    success: response.ok,
    backendEndpoint,
    status: response.status,
    data,
    message: response.ok ? "Token gerado no backend." : data || "Backend recusou a troca do token.",
  };
}

async function enviarEstoqueParaBackend(payload) {
  const dados = payload?.dados || {};
  const backendEndpoint = `${BACKEND_URL}/olist/estoque/webhook`;
  const response = await fetch(backendEndpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      clientId: TINY_CLIENT_ID,
      idProduto: dados.idProduto,
      saldo: dados.saldo,
      tipoEstoque: dados.tipoEstoque,
    }),
  });

  if (!response.ok) {
    throw new Error((await response.text()) || `Backend respondeu HTTP ${response.status}.`);
  }

  return { success: true, backendEndpoint, status: response.status };
}

export default async function handler(req, res) {
  configurarCors(res);
  console.log("[Webhook Olist/Tiny] Requisicao recebida", {
    method: req.method,
    query: req.query || {},
    hasBody: Boolean(req.body),
  });

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }

  if (req.method === "GET") {
    const query = req.query || {};

    if (query.code || query.error) {
      const dadosCallback = normalizarCallback({
        code: query.code,
        state: query.state,
        clientId: query.clientId,
        error: query.error,
        errorDescription: query.error_description,
      });
      const callback = {
        id: `CODE-${Date.now()}-${historico.callbacks.length + 1}`,
        recebidoEm: new Date().toISOString(),
        ...dadosCallback,
      };

      console.log("[Webhook Olist/Tiny] Callback GET recebido", {
        callbackId: callback.id,
        hasCode: Boolean(callback.code),
        state: callback.state,
        error: callback.error,
      });

      if (callback.code && !callback.error) {
        try {
          callback.backend = await enviarCodeParaBackend(callback);
        } catch (error) {
          callback.backend = {
            success: false,
            message: error.message || "Erro ao enviar o code para o backend.",
          };
        }
      }

      historico.callbacks.push(callback);

      res.setHeader("Content-Type", "text/html; charset=utf-8");
      return res.status(callback.error || callback.backend?.success === false ? 400 : 200).send(paginaCallback(callback));
    }

    return res.status(200).json({
      success: true,
      message: "Webhook de callback online.",
      redirectUri: TINY_REDIRECT_URI,
      loginUrl: montarUrlLogin(),
      callbacksTotal: historico.callbacks.length,
      callbacks: historico.callbacks,
      webhooksTotal: historico.webhooks.length,
      webhooks: historico.webhooks,
    });
  }

  if (req.method === "POST") {
    const payload = req.body || {};
    const dadosCallback = normalizarCallback({
      code: payload.code || req.query?.code,
      state: payload.state || req.query?.state,
      clientId: payload.clientId || req.query?.clientId,
      error: payload.error || req.query?.error,
      errorDescription: payload.error_description || payload.errorDescription || req.query?.error_description,
      payload,
    });
    const { code, error } = dadosCallback;

    if (code || error) {
      const callback = {
        id: `CODE-${Date.now()}-${historico.callbacks.length + 1}`,
        recebidoEm: new Date().toISOString(),
        ...dadosCallback,
      };

      console.log("[Webhook Olist/Tiny] Callback POST recebido", {
        callbackId: callback.id,
        hasCode: Boolean(callback.code),
        state: callback.state,
        error: callback.error,
      });

      if (callback.code && !callback.error) {
        try {
          callback.backend = await enviarCodeParaBackend(callback);
        } catch (requestError) {
          console.error("[Webhook Olist/Tiny] Erro ao chamar backend", requestError);
          callback.backend = {
            success: false,
            message: requestError.message || "Erro ao enviar o code para o backend.",
          };
        }
      }

      historico.callbacks.push(callback);

      return res.status(callback.error || callback.backend?.success === false ? 400 : 200).json({
        success: !callback.error && callback.backend?.success !== false,
        message: callback.backend?.message || "Code recebido pelo webhook.",
        callback,
      });
    }

    const evento = {
      id: `WEBHOOK-${Date.now()}-${historico.webhooks.length + 1}`,
      recebidoEm: new Date().toISOString(),
      payload,
    };

    if (payload.tipo === "estoque") {
      try {
        evento.backend = await enviarEstoqueParaBackend(payload);
      } catch (requestError) {
        evento.backend = { success: false, message: requestError.message };
        historico.webhooks.push(evento);
        return res.status(502).json({
          success: false,
          message: "Nao foi possivel aplicar a atualizacao de estoque.",
          evento,
        });
      }
    }

    historico.webhooks.push(evento);

    return res.status(200).json({
      success: true,
      message: "Webhook recebido.",
      evento,
    });
  }

  if (req.method === "DELETE") {
    historico.callbacks.length = 0;
    historico.webhooks.length = 0;

    return res.status(200).json({
      success: true,
      message: "Historico limpo.",
    });
  }

  return res.status(405).json({
    success: false,
    message: "Metodo nao permitido. Use GET, POST ou DELETE.",
  });
}
