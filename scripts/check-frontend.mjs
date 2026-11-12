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

const hologramCss = readFileSync(new URL("../interface-v27.css", import.meta.url), "utf8");
const hologramJs = readFileSync(new URL("../hologram-v24.js", import.meta.url), "utf8");
new vm.Script(hologramJs, { filename: "hologram-v24.js" });
if (!html.includes('id="holoProjection"') || !html.includes('id="headMic"') ||
    !html.includes('href="./hologram-v24.css"') || !html.includes('src="./hologram-v24.js"') ||
    !hologramCss.includes(".projection-frame") || !hologramJs.includes("BidiGenerateContentConstrained") || !hologramJs.includes("action=live-token") || !hologramJs.includes("realtimeInput") || !html.includes('id="headVision"') || !html.includes('id="jamesPower"') || !html.includes('id="worldView"') || !html.includes("james://world/") || !html.includes('action?.type==="windows_protocol"') || !html.includes("navigator.userAgentData?.platform") || !html.includes('id="headCameraFlip"') || !html.includes("Central holográfica de comando") || !html.includes('href="./interface-v27.css"') || html.includes(">◉ CABEÇA</button>")) {
  throw new Error("Holographic command deck, Windows world integration, master power, camera switching, real-time voice, or script integration is missing.");
}

const windowsInstaller = readFileSync(new URL("../windows-companion/Install-JamesCompanion.ps1", import.meta.url), "utf8");
const windowsAgent = readFileSync(new URL("../windows-companion/James-WorldView.ps1", import.meta.url), "utf8");
if (!windowsInstaller.includes("HKCU:\\Software\\Classes\\james") || !windowsAgent.includes("github.com/bilawalsidhu/gods-eye-view.git") || !["install", "open", "update", "stop"].every(action => windowsAgent.includes('"'+action+'"'))) {
  throw new Error("Windows companion installer or allowed world-view actions are missing.");
}

const serviceWorker = readFileSync(new URL("../service-worker.js", import.meta.url), "utf8");
new vm.Script(serviceWorker, { filename: "service-worker.js" });

if (!html.includes('id="voiceOutput"') || !html.includes("if(voiceOutputEnabled)speak(data.reply)")) {
  throw new Error("Voice output must remain user-controllable and connected to AI replies.");
}

const neuralCss = readFileSync(new URL("../james-v3.css", import.meta.url), "utf8");
const neuralVoice = readFileSync(new URL("../james-neural-voice.js", import.meta.url), "utf8");
const liveVoice = hologramJs;
const workerCode = readFileSync(new URL("../worker.js", import.meta.url), "utf8");
const appShell = serviceWorker;
const androidMain = readFileSync(new URL("../android/app/src/main/java/com/james/assistant/MainActivity.kt", import.meta.url), "utf8");
if (!androidMain.includes('put("responseModality", "AUDIO")')) {
  throw new Error("Native Android Live session must explicitly request an audio-mode token.");
}

const androidManifest = readFileSync(new URL("../android/app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
const androidSessionService = readFileSync(new URL("../android/app/src/main/java/com/james/assistant/JamesSessionService.kt", import.meta.url), "utf8");
if (!androidManifest.includes("FOREGROUND_SERVICE_MICROPHONE") ||
    !androidManifest.includes("FOREGROUND_SERVICE_CAMERA") ||
    !androidManifest.includes('android:foregroundServiceType="microphone|camera"') ||
    !androidSessionService.includes("startForeground(NOTIFICATION_ID, notification, types)") ||
    !androidMain.includes("JamesSessionService.start(this)") ||
    !androidMain.includes("A sessão autorizada continua pelo serviço em primeiro plano.")) {
  throw new Error("Android voice/vision foreground session service or explicit start/stop lifecycle is missing.");
}

if (!html.includes('id="jamesNeuralCanvas"') ||
    !html.includes('id="downloadNeuralVoice"') ||
    !html.includes('type="module" src="./james-neural-voice.js"') ||
    !html.includes('href="./james-v3.css"') ||
    !neuralCss.includes(".hero.head-view") ||
    !neuralCss.includes("#jamesNeuralCanvas") ||
    !neuralVoice.includes("Pedro21062014/vozz@v0.2.7") ||
    !neuralVoice.includes("falarEmFluxo") ||
    !neuralVoice.includes("Sintetizador") ||
    !neuralVoice.includes("VOZZ_SYNTH") ||
    !neuralVoice.includes("VOZZ_ASSETS") ||
    !neuralVoice.includes('dispositivo: "auto"') ||
    !neuralVoice.includes("aoProgredir") ||
    !neuralVoice.includes("Piper.carregar") ||
    !neuralVoice.includes('voice: "bruno"') ||
    html.includes("speechSynthesis") ||
    html.includes("SpeechSynthesisUtterance") ||
    html.includes("JamesAndroid.speak") ||
    !liveVoice.includes('responseModalities:["TEXT"]') ||
    !liveVoice.includes('responseModality:"TEXT"') ||
    !liveVoice.includes("window.JamesNeuralVoice?.speak") ||
    !workerCode.includes('responseModality === "AUDIO"') ||
    !workerCode.includes('responseModalities: ["AUDIO"]') ||
    !workerCode.includes('responseModalities: ["TEXT"]') ||
    !workerCode.includes("voiceName: [\"Charon\", \"Algieba\", \"Algenib\"]") ||
    !workerCode.includes("outputAudioTranscription: {}") ||
    !appShell.includes("./james-neural-voice.js") ||
    !appShell.includes("./james-v3.css")) {
  throw new Error("Local Piper/Faber Brazilian Portuguese voice, original voice profiles, redesigned neural command interface, or live text-to-neural-speech integration is missing; system TTS fallback must remain disabled.");
}

console.log("Validated Jâmes v3 command interface, local Piper/Faber pt-BR TTS, voice profiles, live text-to-neural-speech, PWA, and legacy integrations.");
