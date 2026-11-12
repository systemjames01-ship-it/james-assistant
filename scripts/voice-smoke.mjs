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

  await page.goto("http://127.0.0.1:4173/", { waitUntil: "domcontentloaded", timeout: 60000 });
  await page.locator('[data-view-target="control"]').click();
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
