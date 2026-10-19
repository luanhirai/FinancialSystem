const historico = {
  callbacks: [],
  webhooks: [],
};

const TINY_CLIENT_ID =
  process.env.TINY_CLIENT_ID ||
  "tiny-api-479441b5ef29d2e212c3f522ad135840cbfccf54-1779109441";
const TINY_REDIRECT_URI =
  process.env.TINY_REDIRECT_URI || "https://webhook.casalamavievendas.com.br/";
const BACKEND_URL =
  process.env.BACKEND_URL || "https://financeiro.casalamavievendas.com.br/api/backend";
const OLIST_WEBHOOK_SECRET = process.env.OLIST_WEBHOOK_SECRET || "";

const TINY_AUTH_URL =
  process.env.TINY_AUTH_URL ||
  "https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/auth";

function configurarCors(res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
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
  const success = Boolean(callback.code && !callback.error && callback.backend?.success);
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Conexão Olist</title><body><main><h1>${success ? "Conta conectada" : "Não foi possível conectar"}</h1><p>${success ? "Autorização concluída. Você pode fechar esta aba." : "Volte ao perfil e inicie uma nova autorização."}</p></main></body></html>`;
}

async function enviarCodeParaBackend(callback) {
  const backendEndpoint = `${BACKEND_URL}/auth/olist-token/callback`;

  if (!callback.code) {

    return {
      success: false,
      backendEndpoint,
      message: "Callback sem code para enviar ao backend.",
    };
  }

  if (!callback.state) {

    return {
      success: false,
      backendEndpoint,
      message: "Callback sem state/client_id para localizar o usuario.",
    };
  }

  const response = await fetch(backendEndpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "ngrok-skip-browser-warning": "1",
      "X-Webhook-Secret": OLIST_WEBHOOK_SECRET,
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
    headers: {
      "Content-Type": "application/json",
      "ngrok-skip-browser-warning": "1",
      "X-Webhook-Secret": OLIST_WEBHOOK_SECRET,
    },
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

async function enviarVendaParaBackend(payload) {
  const backendEndpoint = `${BACKEND_URL}/olist/vendas/webhook`;
  const headers = {
    "Content-Type": "application/json",
    "ngrok-skip-browser-warning": "1",
      "X-Webhook-Secret": OLIST_WEBHOOK_SECRET,
  };
  if (OLIST_WEBHOOK_SECRET) headers["X-Webhook-Secret"] = OLIST_WEBHOOK_SECRET;

  const response = await fetch(backendEndpoint, {
    method: "POST",
    headers,
    body: JSON.stringify({ ...payload, clientId: TINY_CLIENT_ID }),
  });
  if (!response.ok) {
    throw new Error((await response.text()) || `Backend respondeu HTTP ${response.status}.`);
  }
  return { success: true, backendEndpoint, status: response.status };
}

export default async function handler(req, res) {
  configurarCors(res);

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

      historico.callbacks.push({ id: callback.id }); if (historico.callbacks.length > 10) historico.callbacks.shift();

      res.setHeader("Content-Type", "text/html; charset=utf-8");
      return res.status(callback.error || callback.backend?.success === false ? 400 : 200).send(paginaCallback(callback));
    }

    return res.status(200).json({ success: true, message: "Webhook online." });
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

      if (callback.code && !callback.error) {
        try {
          callback.backend = await enviarCodeParaBackend(callback);
        } catch (requestError) {
          callback.backend = {
            success: false,
            message: requestError.message || "Erro ao enviar o code para o backend.",
          };
        }
      }

      historico.callbacks.push({ id: callback.id }); if (historico.callbacks.length > 10) historico.callbacks.shift();

      return res.status(callback.error || callback.backend?.success === false ? 400 : 200).json({
        success: !callback.error && callback.backend?.success !== false,
        message: callback.backend?.success ? "Conta conectada." : "Não foi possível conectar.",
        callback: { id: callback.id },
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
        historico.webhooks.push({ id: evento.id }); if (historico.webhooks.length > 10) historico.webhooks.shift();
        return res.status(502).json({
          success: false,
          message: "Nao foi possivel aplicar a atualizacao de estoque.",
          evento: { id: evento.id },
        });
      }
    }

    if (["inclusao_pedido", "atualizacao_pedido"].includes(payload.tipo)) {
      try {
        evento.backend = await enviarVendaParaBackend(payload);
      } catch (requestError) {
        evento.backend = { success: false, message: requestError.message };
        historico.webhooks.push({ id: evento.id }); if (historico.webhooks.length > 10) historico.webhooks.shift();
        return res.status(502).json({
          success: false,
          message: "Nao foi possivel salvar o pedido de venda.",
          evento: { id: evento.id },
        });
      }
    }

    historico.webhooks.push({ id: evento.id }); if (historico.webhooks.length > 10) historico.webhooks.shift();

    return res.status(200).json({
      success: true,
      message: "Webhook recebido.",
      evento: { id: evento.id },
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
