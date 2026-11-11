import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
if (scripts.length === 0) {
  throw new Error("No inline JavaScript block found in index.html");
}
for (let i = 0; i < scripts.length; i++) {
  new vm.Script(scripts[i][1], { filename: "index.html:inline-script-" + (i + 1) });
}

const manifest = JSON.parse(readFileSync(new URL("../manifest.webmanifest", import.meta.url), "utf8"));
if (manifest.name !== "Jâmes — Assistente Pessoal" || manifest.display !== "standalone") {
  throw new Error("PWA manifest is missing the expected app identity or standalone display mode.");
}
readFileSync(new URL("../icons/james-icon.svg", import.meta.url), "utf8");

const hologramCss = readFileSync(new URL("../hologram-v24.css", import.meta.url), "utf8");
const hologramJs = readFileSync(new URL("../hologram-v24.js", import.meta.url), "utf8");
new vm.Script(hologramJs, { filename: "hologram-v24.js" });
if (!html.includes('id="jamesGlobeCanvas"') || !html.includes('id="headMic"') ||
    !html.includes('href="./hologram-v24.css"') || !html.includes('src="./hologram-v24.js"') ||
    !hologramCss.includes(".globe-stage") || !hologramJs.includes("BidiGenerateContentConstrained") || !hologramJs.includes("action=live-token") || !hologramJs.includes("realtimeInput") || !html.includes('id="headVision"')) {
  throw new Error("Holographic globe, real-time voice, live vision, or script integration is missing.");
}

const serviceWorker = readFileSync(new URL("../service-worker.js", import.meta.url), "utf8");
new vm.Script(serviceWorker, { filename: "service-worker.js" });

if (!html.includes('id="voiceOutput"') || !html.includes("if(voiceOutputEnabled)speak(data.reply)")) {
  throw new Error("Voice output must remain user-controllable and connected to AI replies.");
}
console.log("Validated inline scripts, holographic globe assets, voice interaction, PWA manifest, icon, service worker, and voice controls.");
