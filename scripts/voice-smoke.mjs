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
  await page.route("https://upload.wikimedia.org/**", async route => {
    await route.fulfill({ status: 200, contentType: "image/svg+xml", body: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#28a9c5"/></svg>' });
  });
  await page.route("https://james-ai.systemjames01.workers.dev/**", async route => {
    const origin = route.request().headers().origin || "*";
    const headers = {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    };
    if (route.request().method() === "OPTIONS") {
      await route.fulfill({ status: 204, headers });
      return;
    }
    const url = new URL(route.request().url());
    const action = url.searchParams.get("action");
    const body = action === "history" ? { messages: [], hasMore: false, nextBefore: null }
      : action === "tasks" ? { tasks: [] }
      : action === "memories" ? { memories: [] }
      : { status: "online", aiConfigured: true, databaseConfigured: true };
    await route.fulfill({ status: 200, contentType: "application/json", headers, body: JSON.stringify(body) });
  });

  await page.goto("http://127.0.0.1:4173/", { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.evaluate(() => window.showHoloVisuals([{
    title: "Imagem de teste",
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/test.jpg",
    sourceUrl: "https://commons.wikimedia.org/wiki/File:Test.jpg",
    credit: "Teste",
    license: "CC0"
  }]));
  await page.locator("#holoVisuals img").waitFor({ state: "visible" });
  if (await page.locator("#holoVisuals strong").textContent() !== "Imagem de teste") {
    throw new Error("A projeção holográfica não apresentou a imagem ilustrativa.");
  }
  await page.evaluate(() => window.stopHoloVisuals());
  const primaryTabs = page.locator(".module-nav [data-view-target]");
  if (await primaryTabs.count() !== 3) throw new Error("A interface precisa apresentar exatamente três áreas principais.");
  if (await page.locator("#jamesPower").getAttribute("aria-pressed") !== "false") {
    throw new Error("O núcleo não pode ativar microfone/câmera automaticamente ao abrir a página.");
  }
  await page.locator('.module-nav [data-view-target="intelligence"]').click();
  await page.locator("#intelligenceView").waitFor({ state: "visible" });
  const firstConversation = await page.evaluate(() => localStorage.getItem("james_active_session_id"));
  await page.locator("#newConversation").click();
  await page.waitForFunction(previous => localStorage.getItem("james_active_session_id") !== previous, firstConversation);
  const secondConversation = await page.evaluate(() => localStorage.getItem("james_active_session_id"));
  if (!firstConversation || !secondConversation || firstConversation === secondConversation) {
    throw new Error("Criar conversa não gerou uma sessão independente.");
  }
  await page.locator("#conversationPicker").selectOption(firstConversation);
  await page.waitForFunction(previous => localStorage.getItem("james_active_session_id") === previous, firstConversation);
  await page.locator('.module-nav [data-view-target="control"]').click();
  await page.locator("#controlView").waitFor({ state: "visible" });
  await page.locator('.module-nav [data-view-target="overview"]').click();
  await page.locator("#overviewView").waitFor({ state: "visible" });
  await page.locator('.module-nav [data-view-target="control"]').click();
  await page.locator('#controlView [data-view-target="voices"]').click();
  await page.locator("#downloadNeuralVoice").waitFor({ state: "visible", timeout: 30000 });
  await page.locator("#downloadNeuralVoice").click();

  await page.waitForFunction(
    () => Boolean(window.JamesNeuralVoice?.isReady?.()),
    null,
    { timeout: 240000 }
  );

  const spoke = await page.evaluate(async () => {
    return await window.JamesNeuralVoice.speak(
      "Olá. Eu sou Jâmes. Esta é uma verificação da voz neural masculina em português brasileiro."
    );
  });

  if (!spoke) throw new Error("O motor Piper carregou, mas não concluiu a síntese e reprodução de áudio.");
  if (pageErrors.length) throw new Error("Erros JavaScript na página: " + pageErrors.join(" | "));

  process.stdout.write("VOICE SMOKE PASSED: Piper/Faber carregou, gerou áudio e reproduziu no Chromium mobile viewport.\n");
} finally {
  await browser.close();
}
