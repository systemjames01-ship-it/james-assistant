import test from "node:test";
import assert from "node:assert/strict";
import worker from "../worker.js";

const origin = "https://systemjames01-ship-it.github.io";

test("health endpoint reports the configured primary model", async () => {
  const response = await worker.fetch(new Request("https://worker.test/"), {});
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.status, "online");
  assert.equal(body.model, "gemini-3.8-flash");
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
    if (urls.length <= 2) {
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
    assert.equal(body.model, "gemini-3.5-flash-lite");
    assert.equal(urls.length, 3);
    assert.ok(urls[0].includes("gemini-3.8-flash"));
    assert.ok(urls[2].includes("gemini-3.5-flash-lite"));
  } finally {
    globalThis.fetch = originalFetch;
  }
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
