import { chromium } from "playwright";

const browser = await chromium.launch({
  headless: true,
  args: ["--autoplay-policy=no-user-gesture-required"]
});

try {
  const page = await browser.newPage({
    viewport: { width: 360, height: 800 },
    deviceScaleFactor: 1
  });
  const pageErrors = [];
  page.on("pageerror", error => pageErrors.push(error.message));

  // Browser APIs are deterministic in CI; this exercises the real UI flow,
  // recognition callbacks, Worker request, spoken reply, and listening restart.
  await page.addInitScript(() => {
    window.__recognitions = [];
    window.webkitSpeechRecognition = class {
      constructor() {
        this.lang = "";
        this.continuous = false;
        this.interimResults = false;
        this.maxAlternatives = 1;
        window.__recognitions.push(this);
      }
      start() { queueMicrotask(() => this.onstart?.()); }
      stop() { queueMicrotask(() => this.onend?.()); }
    };
    window.SpeechRecognition = window.webkitSpeechRecognition;
    window.SpeechSynthesisUtterance = class {
      constructor(text) { this.text = text; this.lang = ""; this.rate = 1; }
    };
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: {
        speaking: false,
        cancel() { this.speaking = false; },
        speak(utterance) {
          this.speaking = true;
          window.__spoken = [...(window.__spoken || []), utterance.text];
          queueMicrotask(() => {
            this.speaking = false;
            utterance.onend?.();
          });
        }
      }
    });
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init = {}) => {
      const url = String(input);
      if (url.includes("james-ai.systemjames01.workers.dev")) {
        if (!init.method || init.method === "GET") {
          return new Response(JSON.stringify({
            status: "online", aiConfigured: true, databaseConfigured: false
          }), { status: 200, headers: { "Content-Type": "application/json" } });
        }
        window.__workerRequest = JSON.parse(init.body || "{}");
        const encoder = new TextEncoder();
        const chunks = [
          'data: {"candidates":[{"content":{"parts":[{"text":"Olá, Jamerson. "}]}}]}\\n\\n',
          'data: {"candidates":[{"content":{"parts":[{"text":"Estou ouvindo você."}]}}]}\\n\\n'
        ];
        const body = new ReadableStream({
          async start(controller) {
            controller.enqueue(encoder.encode(chunks[0]));
            await new Promise(resolve => setTimeout(resolve, 180));
            window.__secondStreamChunkSent = true;
            controller.enqueue(encoder.encode(chunks[1]));
            controller.close();
          }
        });
        return new Response(body, {
          status: 200,
          headers: { "Content-Type": "text/event-stream; charset=utf-8" }
        });
      }
      return nativeFetch(input, init);
    };
  });

  await page.goto("http://127.0.0.1:4173/", {
    waitUntil: "domcontentloaded",
    timeout: 60000
  });

  await page.locator("#power").click();
  await page.locator("#listen").click();
  await page.waitForFunction(() => window.__recognitions?.length === 1);
  await page.waitForFunction(() => window.__recognitions[0]?.onstart !== undefined);

  await page.evaluate(() => {
    const recognition = window.__recognitions[0];
    recognition.onresult?.({
      results: [[{ transcript: "Jâmes, como você está?" }]]
    });
    recognition.onend?.();
  });

  await page.locator("#messages .msg.bot").filter({
    hasText: "Olá, Jamerson. Estou ouvindo você."
  }).waitFor({ state: "attached", timeout: 15000 });
  await page.waitForFunction(
    () => window.__workerRequest?.message === "Jâmes, como você está?"
  );
  await page.waitForFunction(
    () => window.__spoken?.some(text => text.includes("Estou ouvindo você"))
  );
  await page.waitForFunction(() => window.__recognitions?.length >= 2, {
    timeout: 10000
  });

  const result = await page.evaluate(() => ({
    request: window.__workerRequest,
    spoken: window.__spoken,
    recognitions: window.__recognitions.length,
    voiceLabel: document.querySelector("#voiceModeLabel")?.textContent
  }));

  if (result.request.message !== "Jâmes, como você está?") {
    throw new Error("A fala reconhecida não chegou ao Worker.");
  }
  if (result.recognitions < 2) {
    throw new Error("O reconhecimento não foi reiniciado após a resposta.");
  }
  if (pageErrors.length) {
    throw new Error("Erros JavaScript na página: " + pageErrors.join(" | "));
  }

  process.stdout.write(
    "VOICE SMOKE PASSED: ativação única, reconhecimento pt-BR, envio ao Worker, resposta falada e retomada da escuta.\n"
  );
} finally {
  await browser.close();
}
