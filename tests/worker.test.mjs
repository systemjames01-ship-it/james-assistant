import test from "node:test";
import assert from "node:assert/strict";
import worker from "../worker.js";

const origin = "https://systemjames01-ship-it.github.io";
// Public status intentionally omits internal model-provider identifiers.

test("health endpoint reports Jâmes status without exposing the underlying model", async () => {
  const response = await worker.fetch(new Request("https://worker.test/"), {});
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.status, "online");
  assert.equal(body.model, undefined);
  assert.equal(JSON.stringify(body).toLowerCase().includes("gemini"), false);
  assert.equal(body.aiConfigured, false);
  assert.equal(body.databaseConfigured, false);
  assert.ok(body.capabilities.includes("unified_context"));
});

test("POST works without D1 and accepts image and PDF attachments", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Analisei os anexos." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Analise estes arquivos",
        sessionId: "session_test_12345",
        mode: "assistant",
        attachments: [
          { name: "foto.png", dataUrl: "data:image/png;base64,aGVsbG8=" },
          { name: "relatorio.pdf", dataUrl: "data:application/pdf;base64,aGVsbG8=" }
        ]
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Analisei os anexos.");
    assert.equal(body.memory, "temporarily_unavailable");
    assert.equal(sentBody.contents[0].parts.filter(part => part.inlineData).length, 2);
    assert.deepEqual(sentBody.contents[0].parts.filter(part => part.inlineData).map(part => part.inlineData.mimeType), ["image/png", "application/pdf"]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("fallback model is used after temporary primary-model failure", async () => {
  const originalFetch = globalThis.fetch;
  const urls = [];
  globalThis.fetch = async (url) => {
    urls.push(String(url));
    if (urls.length === 1) {
      return new Response(JSON.stringify({ error: { message: "temporarily unavailable" } }), {
        status: 503,
        headers: { "Content-Type": "application/json" }
      });
    }
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Resposta pelo modelo alternativo." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Teste de fallback",
        sessionId: "session_test_12345",
        mode: "assistant"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Resposta pelo modelo alternativo.");
    assert.equal(body.model, undefined);
    assert.equal(urls.length, 2);
    assert.ok(urls[0].includes("gemini-3.8-flash"));
    assert.ok(urls[1].includes("gemini-3.5-flash-lite"));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("agent can search the public web through a tool call", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  let searchCalled = false;
  globalThis.fetch = async (url, options) => {
    if (String(url).includes("duckduckgo.com")) {
      searchCalled = true;
      return new Response('<a class="result__a" href="https://www.gov.br/energia">Portal oficial de energia</a><div class="result__snippet">Dados oficiais.</div>', { status: 200, headers: { "Content-Type": "text/html" } });
    }
    calls++;
    if (calls === 1) return new Response(JSON.stringify({ candidates: [{ content: { role: "model", parts: [{ functionCall: { name: "search_public_web", args: { queries: ["dados oficiais energia Brasil", "previsão de fornecimento energia"] } } }] } }] }), { status: 200 });
    const functionResult = JSON.parse(options.body).contents.at(-1).parts[0].functionResponse.response;
    assert.equal(functionResult.success, true);
    assert.ok(functionResult.results[0].url.includes("gov.br"));
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Encontrei uma fonte oficial." }] } }] }), { status: 200 });
  };
  try {
    const request = new Request("https://worker.test/", { method: "POST", headers: { Origin: origin, "Content-Type": "application/json" }, body: JSON.stringify({ message: "Investigue dados oficiais de energia", sessionId: "session_test_12345" }) });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Encontrei uma fonte oficial.");
    assert.equal(searchCalled, true);
  } finally { globalThis.fetch = originalFetch; }
});

test("history endpoint reports unavailable D1 without crashing", async () => {
  const db = {
    prepare() {
      return { bind() { return { all: async () => { throw new Error("table unavailable"); } }; } };
    }
  };
  const request = new Request("https://worker.test/?action=history&sessionId=session_test_12345", {
    headers: { Origin: origin }
  });
  const response = await worker.fetch(request, { DB: db });
  const body = await response.json();
  assert.equal(response.status, 503);
  assert.deepEqual(body.messages, []);
});

test("assistant executes calculator tool and returns its result to Gemini", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (_url, options) => {
    const body = JSON.parse(options.body);
    requests.push(body);
    if (requests.length === 1) {
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{
          functionCall: { name: "calculate", args: { expression: "(12 * 3) + 4" } }
        }] } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "O resultado é 40." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({ message: "Quanto é (12 vezes 3) mais 4?", sessionId: "session_test_12345", mode: "assistant" })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "O resultado é 40.");
    const functionResponse = requests[1].contents.at(-1).parts[0].functionResponse;
    assert.equal(functionResponse.name, "calculate");
    assert.equal(functionResponse.response.result, 40);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("assistant hands explicit God's Eye View requests to the Windows companion", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (_url, options) => {
    const body = JSON.parse(options.body);
    requests.push(body);
    if (requests.length === 1) {
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{
          functionCall: { name: "windows_gods_eye_view", args: { action: "open" } }
        }] } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    const toolResponse = requests[1].contents.at(-1).parts[0].functionResponse.response;
    assert.equal(toolResponse.queuedForDevice, true);
    assert.equal(toolResponse.clientAction.type, "windows_protocol");
    assert.equal(toolResponse.clientAction.uri, "james://world/open");
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Comando encaminhado ao componente Windows." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({ message: "Abra o God's Eye View no meu Windows", sessionId: "session_test_12345", mode: "assistant" })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Comando encaminhado ao componente Windows.");
    assert.deepEqual(body.clientActions, [{ type: "windows_protocol", uri: "james://world/open" }]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("live tool endpoint returns only allowlisted Windows protocol commands", async () => {
  const request = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "windows_gods_eye_view", args: { action: "update" } })
  });
  const response = await worker.fetch(request, {});
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.result.clientAction.uri, "james://world/update");
  const denied = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "windows_gods_eye_view", args: { action: "format_disk" } })
  });
  const deniedResponse = await worker.fetch(denied, {});
  assert.equal(deniedResponse.status, 200);
  assert.equal((await deniedResponse.json()).result.error, "Ação Windows não permitida.");
});

test("open_url tool only returns allowlisted HTTP and HTTPS client actions", async () => {
  const request = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "open_url", args: { url: "https://example.com/control" } })
  });
  const response = await worker.fetch(request, {});
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.result.clientAction.type, "open_url");
  assert.equal(body.result.clientAction.url, "https://example.com/control");

  const denied = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "open_url", args: { url: "javascript:alert(1)" } })
  });
  const deniedResponse = await worker.fetch(denied, {});
  assert.equal(deniedResponse.status, 200);
  assert.match((await deniedResponse.json()).result.error, /HTTP\/HTTPS/);
});

test("assistant creates a persistent task through the D1 tool", async () => {
  const originalFetch = globalThis.fetch;
  const executedSql = [];
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          return {
            all: async () => ({ results: [] }),
            run: async () => {
              executedSql.push({ sql, values });
              return { meta: { last_row_id: 17, changes: 1 } };
            }
          };
        }
      };
    },
    batch: async () => []
  };
  let requestCount = 0;
  globalThis.fetch = async () => {
    requestCount++;
    if (requestCount === 1) {
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{
          functionCall: { name: "create_task", args: { title: "Comprar pão", details: "Passar na padaria" } }
        }] } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Tarefa criada: Comprar pão." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({ message: "Crie uma tarefa para comprar pão", sessionId: "session_test_12345", mode: "assistant" })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Tarefa criada: Comprar pão.");
    assert.ok(executedSql.some(item => item.sql.includes("INSERT INTO james_tasks_v21")));
    assert.ok(executedSql.some(item => item.values.includes("session_test_12345")));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("the unified Jâmes core keeps one context and tools available in every conversation", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  let historyBindings;
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          if (sql.includes("SELECT role, content")) {
            historyBindings = values;
            return { all: async () => ({ results: [
              { role: "user", content: "Lembre que meu projeto se chama Jâmes." },
              { role: "model", content: "Claro, vou manter esse contexto." }
            ] }) };
          }
          return { run: async () => ({ meta: { changes: 1 } }) };
        }
      };
    },
    batch: async () => []
  };
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Contexto compartilhado recebido." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Qual é o nome do projeto?",
        sessionId: "session_test_12345",
        mode: "chat"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Contexto compartilhado recebido.");
    assert.deepEqual(historyBindings, ["session_test_12345", "session_test_12345_assistant", "session_test_12345_chat"]);
    assert.ok(sentBody.contents.some(item => item.parts?.some(part => part.text === "Lembre que meu projeto se chama Jâmes.")));
    assert.ok(sentBody.tools[0].functionDeclarations.some(tool => tool.name === "android_open_app"));
    assert.equal(sentBody.systemInstruction.parts[0].text.includes("Você não é apenas um chatbot."), true);
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test("complex assistant requests use high reasoning without a fixed response timeout", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Plano analisado com cuidado." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Analise a arquitetura do projeto, compare as alternativas, identifique riscos e proponha um plano de implementação em várias etapas.",
        sessionId: "session_test_12345",
        mode: "assistant"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reasoningLevel, "high");
    assert.equal(sentBody.generationConfig.thinkingConfig.thinkingLevel, "high");
    assert.equal(sentBody.generationConfig.maxOutputTokens, 8192);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("ordinary conversation keeps balanced medium reasoning", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Tudo bem por aqui." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Oi, tudo bem?",
        sessionId: "session_test_12345",
        mode: "chat"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reasoningLevel, "medium");
    assert.equal(sentBody.generationConfig.thinkingConfig.thinkingLevel, "medium");
    assert.equal(sentBody.generationConfig.maxOutputTokens, 6144);
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test("assistant injects only explicitly stored memories into model context", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          if (sql.includes("SELECT role, content")) {
            return { all: async () => ({ results: [] }) };
          }
          if (sql.includes("SELECT memory_key, memory_value")) {
            return { all: async () => ({ results: [
              { memory_key: "project_name", memory_value: "O projeto se chama Jâmes." }
            ] }) };
          }
          return { run: async () => ({ meta: { changes: 1 } }) };
        }
      };
    },
    batch: async () => []
  };
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "O projeto se chama Jâmes." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Qual é o nome do meu projeto?",
        sessionId: "session_test_12345",
        mode: "assistant"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.savedMemories, 1);
    assert.match(sentBody.systemInstruction.parts[0].text, /O projeto se chama Jâmes/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("assistant saves a memory only through the explicit save-memory tool", async () => {
  const originalFetch = globalThis.fetch;
  const statements = [];
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          return {
            all: async () => ({ results: [] }),
            run: async () => {
              statements.push({ sql, values });
              return { meta: { changes: 1, last_row_id: 23 } };
            }
          };
        }
      };
    },
    batch: async () => []
  };
  let requestCount = 0;
  globalThis.fetch = async (_url, options) => {
    requestCount++;
    const body = JSON.parse(options.body);
    if (requestCount === 1) {
      assert.ok(body.tools[0].functionDeclarations.some(tool => tool.name === "save_memory"));
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{
          functionCall: { name: "save_memory", args: { key: "preferencias", value: "O usuário prefere custo zero." } }
        }] } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Vou guardar essa preferência." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Lembre que prefiro manter o projeto sem custos.",
        sessionId: "session_test_12345",
        mode: "assistant"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Vou guardar essa preferência.");
    assert.ok(statements.some(item => item.sql.includes("INSERT INTO james_memories_v21")));
    assert.ok(statements.some(item => item.values.includes("O usuário prefere custo zero.")));
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test("assistant hands an explicitly requested app launch to the native Android client", async () => {
  const originalFetch = globalThis.fetch;
  let requestCount = 0;
  globalThis.fetch = async (_url, options) => {
    requestCount++;
    const sent = JSON.parse(options.body);
    if (requestCount === 1) {
      assert.ok(sent.tools[0].functionDeclarations.some(tool => tool.name === "android_open_app"));
      return new Response(JSON.stringify({
        candidates: [{ content: { role: "model", parts: [{
          functionCall: { name: "android_open_app", args: { app_name: "WhatsApp" } }
        }] } }]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Solicitei a abertura do WhatsApp no Android." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({ message: "Abra o WhatsApp", sessionId: "session_test_12345", mode: "assistant" })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.deepEqual(body.clientActions, [{ type: "open_app", appName: "WhatsApp" }]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("assistant receives device timezone context for local scheduling", async () => {
  const originalFetch = globalThis.fetch;
  let sentBody;
  globalThis.fetch = async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return new Response(JSON.stringify({
      candidates: [{ content: { parts: [{ text: "Entendi o horário local." }] } }]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Que horas são?",
        sessionId: "session_test_12345",
        mode: "assistant",
        deviceContext: {
          platform: "android",
          timezone: "America/Bahia",
          localNow: "2026-11-10T20:30:00-03:00",
          device: { manufacturer: "Samsung", model: "SM-A135M", androidVersion: "14", batteryPercent: 72, charging: false, networkConnected: true }
        }
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.match(sentBody.systemInstruction.parts[0].text, /America\/Bahia/);
    assert.match(sentBody.systemInstruction.parts[0].text, /2026-11-10T20:30:00-03:00/);
    assert.match(sentBody.systemInstruction.parts[0].text, /Samsung SM-A135M/);
    assert.match(sentBody.systemInstruction.parts[0].text, /Bateria: 72%/);
    assert.match(sentBody.systemInstruction.parts[0].text, /Conectividade validada: conectada/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("explicit Android UI action is handed to the native accessibility bridge", async () => {
  const originalFetch = globalThis.fetch;
  let count = 0;
  globalThis.fetch = async (_url, options) => {
    count++;
    if (count === 1) return new Response(JSON.stringify({ candidates: [{ content: { role: "model", parts: [{ functionCall: { name: "android_ui_action", args: { action: "tap_text", target_text: "Configurações" } } }] } }] }), { status: 200 });
    const body = JSON.parse(options.body);
    const result = body.contents.at(-1).parts[0].functionResponse.response;
    assert.equal(result.queuedForDevice, true);
    assert.equal(result.clientAction.type, "ui_action");
    assert.equal(result.clientAction.action, "tap_text");
    assert.equal(result.clientAction.targetText, "Configurações");
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "Ação encaminhada ao Android." }] } }] }), { status: 200 });
  };
  try {
    const request = new Request("https://worker.test/", { method: "POST", headers: { Origin: origin, "Content-Type": "application/json" }, body: JSON.stringify({ message: "Toque em Configurações", sessionId: "session_test_12345" }) });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "test-key" });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Ação encaminhada ao Android.");
    assert.equal(count, 2);
  } finally { globalThis.fetch = originalFetch; }
});

test("live token endpoint returns a one-use token without exposing the API key", async () => {
  const originalFetch = globalThis.fetch;
  let provisionBody;
  let lastIssuedAt = null;
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          return {
            first: async () => lastIssuedAt === null ? null : { last_issued_at: lastIssuedAt },
            run: async () => {
              if (sql.startsWith("INSERT INTO james_live_rate_limits")) lastIssuedAt = values[1];
              return { success: true };
            }
          };
        },
        run: async () => ({ success: true })
      };
    }
  };
  globalThis.fetch = async (url, options) => {
    assert.equal(String(url), "https://generativelanguage.googleapis.com/v1beta/auth_tokens");
    assert.equal(options.headers["x-goog-api-key"], "secret-test-key");
    provisionBody = JSON.parse(options.body);
    return new Response(JSON.stringify({ name: "auth_tokens/short-lived" }), { status: 200 });
  };
  try {
    const request = new Request("https://worker.test/?action=live-token", {
      method: "POST",
      headers: { Origin: origin, "CF-Connecting-IP": "192.0.2.20", "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId: "session_test_12345", voiceProfile: 2 })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "secret-test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.token, "auth_tokens/short-lived");
    assert.equal(body.model, undefined);
    assert.equal(JSON.stringify(body).includes("secret-test-key"), false);
    assert.equal(provisionBody.uses, 1);
    assert.equal(provisionBody.liveConnectConstraints.model, "models/gemini-3.8-live");
    assert.deepEqual(provisionBody.liveConnectConstraints.config.generationConfig.responseModalities, ["AUDIO"]);
    assert.deepEqual(provisionBody.liveConnectConstraints.config.generationConfig.speechConfig, {
      languageCode: "pt-BR",
      voiceConfig: { prebuiltVoiceConfig: { voiceName: "Algenib" } }
    });
    assert.deepEqual(provisionBody.liveConnectConstraints.config.outputAudioTranscription, {});
    assert.deepEqual(provisionBody.liveConnectConstraints.config.realtimeInputConfig.automaticActivityDetection, { disabled: false });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("browser Live sessions receive text-mode tokens for local neural speech", async () => {
  const originalFetch = globalThis.fetch;
  let provisionBody;
  const db = {
    prepare() {
      return {
        run: async () => ({ success: true }),
        bind() {
          return {
            first: async () => null,
            all: async () => ({ results: [] }),
            run: async () => ({ success: true })
          };
        }
      };
    }
  };
  globalThis.fetch = async (_url, options) => {
    provisionBody = JSON.parse(options.body);
    return new Response(JSON.stringify({ name: "auth_tokens/text-session" }), { status: 200 });
  };
  try {
    const request = new Request("https://worker.test/?action=live-token", {
      method: "POST",
      headers: { Origin: origin, "CF-Connecting-IP": "192.0.2.21", "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId: "session_test_12345", voiceProfile: 1, responseModality: "TEXT" })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "secret-test-key", DB: db });
    assert.equal(response.status, 200);
    assert.deepEqual(provisionBody.liveConnectConstraints.config.generationConfig, { responseModalities: ["TEXT"] });
    assert.equal(provisionBody.liveConnectConstraints.config.outputAudioTranscription, undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("live tool endpoint executes allowlisted tools and rejects unknown tools", async () => {
  const calculateRequest = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "calculate", args: { expression: "6 * 7" } })
  });
  const calculateResponse = await worker.fetch(calculateRequest, {});
  const calculateBody = await calculateResponse.json();
  assert.equal(calculateResponse.status, 200);
  assert.equal(calculateBody.result.result, 42);

  const deniedRequest = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId: "session_test_12345", name: "delete_everything", args: {} })
  });
  const deniedResponse = await worker.fetch(deniedRequest, {});
  assert.equal(deniedResponse.status, 400);
});


test("paired devices can list and reopen conversations stored under the same profile", async () => {
  const registered = [];
  const db = {
    async batch() { return []; },
    prepare(sql) {
      return {
        run: async () => ({ success: true }),
        bind(...values) {
          return {
            run: async () => {
              if (sql.includes("INSERT INTO james_profile_conversations_v21")) {
                registered.push({ profile_id: values[0], session_id: values[1], title: values[2] });
              }
              return { success: true };
            },
            first: async () => null,
            all: async () => ({
              results: sql.includes("FROM james_profile_conversations_v21")
                ? registered.filter(item => item.profile_id === values[0]).map(item => ({
                    session_id: item.session_id, title: item.title, created_at: "2026-11-11 10:00:00", updated_at: "2026-11-11 10:01:00"
                  }))
                : []
            })
          };
        }
      };
    }
  };
  const save = await worker.fetch(new Request("https://worker.test/?action=live-turn", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: "session_chat_pair",
      profileId: "profile_owner_pair",
      userText: "Explique os motores de foguete",
      modelText: "Um motor de foguete gera empuxo."
    })
  }), { DB: db });
  assert.equal(save.status, 200);
  const list = await worker.fetch(new Request("https://worker.test/?action=conversations&profileId=profile_owner_pair", {
    headers: { Origin: origin }
  }), { DB: db });
  const body = await list.json();
  assert.equal(list.status, 200);
  assert.equal(body.conversations[0].session_id, "session_chat_pair");
  assert.equal(body.conversations[0].title, "Explique os motores de foguete");
});

test("Worker applies a per-IP safety limit to repeated live tool requests", async () => {
  const ip = "198.51.100.77";
  let lastStatus = 0;
  for (let index = 0; index < 31; index++) {
    const request = new Request("https://worker.test/?action=live-tool", {
      method: "POST",
      headers: { Origin: origin, "CF-Connecting-IP": ip, "Content-Type": "application/json" },
      body: "{"
    });
    const response = await worker.fetch(request, {});
    lastStatus = response.status;
    if (index < 30) assert.equal(response.status, 400);
  }
  assert.equal(lastStatus, 429);
});

test("text agent returns Commons visuals to the holographic client", async () => {
  const originalFetch = globalThis.fetch;
  let geminiCalls = 0;
  const db = {
    prepare() {
      return {
        run: async () => ({ meta: { changes: 1, last_row_id: 1 } }),
        bind() {
          return {
            run: async () => ({ meta: { changes: 1, last_row_id: 1 } }),
            all: async () => ({ results: [] }),
            first: async () => null
          };
        }
      };
    }
  };
  globalThis.fetch = async (url) => {
    const target = String(url);
    if (target.includes("generativelanguage.googleapis.com")) {
      geminiCalls++;
      const body = geminiCalls === 1
        ? { candidates: [{ content: { role: "model", parts: [{ functionCall: { name: "search_public_images", args: { query: "solar system" }, id: "image-call-1" } }] } }] }
        : { candidates: [{ content: { role: "model", parts: [{ text: "Aqui está uma imagem ilustrativa do sistema solar." }] } }] };
      return new Response(JSON.stringify(body), { status: 200 });
    }
    if (target.startsWith("https://commons.wikimedia.org/w/api.php?")) {
      return new Response(JSON.stringify({
        query: { pages: { 1: { title: "File:Solar.jpg", imageinfo: [{
          thumburl: "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Solar.jpg/1200px-Solar.jpg",
          url: "https://upload.wikimedia.org/wikipedia/commons/a/a1/Solar.jpg",
          descriptionurl: "https://commons.wikimedia.org/wiki/File:Solar.jpg",
          mime: "image/jpeg",
          extmetadata: { LicenseShortName: { value: "CC0" }, Artist: { value: "Commons author" } }
        }] } } }
      }), { status: 200 });
    }
    throw new Error("Unexpected mocked URL: " + target);
  };
  try {
    const request = new Request("https://worker.test/", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: "Mostre o sistema solar",
        sessionId: "session_visual_chat",
        profileId: "profile_visual_chat"
      })
    });
    const response = await worker.fetch(request, { GEMINI_API_KEY: "secret-test-key", DB: db });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.reply, "Aqui está uma imagem ilustrativa do sistema solar.");
    assert.equal(body.visuals.length, 1);
    assert.equal(geminiCalls, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("visual search returns only sourced Wikimedia Commons image URLs", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    assert.match(String(url), /^https:\/\/commons\.wikimedia\.org\/w\/api\.php\?/);
    return new Response(JSON.stringify({
      query: {
        pages: {
          1: {
            title: "File:Solar System illustration.jpg",
            imageinfo: [{
              thumburl: "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Solar.jpg/1200px-Solar.jpg",
              url: "https://upload.wikimedia.org/wikipedia/commons/a/a1/Solar.jpg",
              descriptionurl: "https://commons.wikimedia.org/wiki/File:Solar_System_illustration.jpg",
              mime: "image/jpeg",
              extmetadata: {
                LicenseShortName: { value: "CC BY-SA 4.0" },
                Artist: { value: "Example Artist" }
              }
            }]
          }
        }
      }
    }), { status: 200 });
  };
  try {
    const request = new Request("https://worker.test/?action=live-tool", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        sessionId: "session_visual_123",
        profileId: "profile_visual_123",
        name: "search_public_images",
        args: { query: "solar system" }
      })
    });
    const response = await worker.fetch(request, {});
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.result.visuals.length, 1);
    assert.equal(body.result.visuals[0].license, "CC BY-SA 4.0");
    assert.equal(new URL(body.result.visuals[0].imageUrl).hostname, "upload.wikimedia.org");
    assert.equal(new URL(body.result.visuals[0].sourceUrl).hostname, "commons.wikimedia.org");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("personal memories are shared across independent chats inside one profile", async () => {
  const statements = [];
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          return {
            run: async () => {
              statements.push({ sql, values });
              return { meta: { changes: 1 } };
            },
            all: async () => {
              statements.push({ sql, values });
              if (sql.includes("FROM james_memories_v21")) {
                return { results: [{ id: 9, memory_key: "preferencia", memory_value: "Responder em português brasileiro." }] };
              }
              return { results: [] };
            }
          };
        }
      };
    }
  };
  const saveRequest = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: "session_chat_one",
      profileId: "profile_owner_12345",
      name: "save_memory",
      args: { key: "preferencia", value: "Responder em português brasileiro." }
    })
  });
  const saved = await worker.fetch(saveRequest, { DB: db });
  assert.equal(saved.status, 200);
  assert.ok(statements.some(item => item.sql.includes("INSERT INTO james_memories_v21") && item.values[0] === "profile_owner_12345"));

  const listRequest = new Request("https://worker.test/?action=live-tool", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: "session_chat_two",
      profileId: "profile_owner_12345",
      name: "list_memories",
      args: {}
    })
  });
  const listed = await worker.fetch(listRequest, { DB: db });
  const body = await listed.json();
  assert.equal(listed.status, 200);
  assert.equal(body.result.memories[0].value, "Responder em português brasileiro.");
  assert.ok(statements.some(item => item.sql.includes("FROM james_memories_v21") && item.values[0] === "profile_owner_12345"));
});

test("live voice transcript is persisted into the same shared conversation history", async () => {
  const batches = [];
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          return { sql, values };
        }
      };
    },
    batch: async statements => { batches.push(statements); return []; }
  };
  const request = new Request("https://worker.test/?action=live-turn", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({
      sessionId: "session_test_12345",
      userText: "Qual é o estado do projeto?",
      modelText: "O projeto está em desenvolvimento."
    })
  });
  const response = await worker.fetch(request, { DB: db });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.saved, true);
  assert.equal(batches.length, 1);
  assert.equal(batches[0][0].values[0], "session_test_12345");
  assert.equal(batches[0][0].values[1], "Qual é o estado do projeto?");
  assert.equal(batches[0][1].values[0], "session_test_12345");
  assert.equal(batches[0][1].values[1], "O projeto está em desenvolvimento.");
});
