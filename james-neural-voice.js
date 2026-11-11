import { FilesetResolver, TextToSpeech } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-audio@1.2.2/+esm";

(() => {
  "use strict";

  const MODEL_URL = "https://huggingface.co/litert-community/Kokoro-82M/resolve/main/kokoro_int8_gpu_fp16.litertlm";
  const WASM_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-audio@1.2.2/wasm";
  const profiles = {
    jarvis: { label: "Jâmes · Clássica", voiceId: "pm_alex", rate: 1.0 },
    patrick: { label: "Jâmes · Analítica", voiceId: "pm_alex", rate: 1.045 },
    demon: { label: "Jâmes · Intensa", voiceId: "pm_santa", rate: 0.94 }
  };

  let wasmFileset = null;
  let engine = null;
  let loading = null;
  let audioContext = null;
  let nextStartAt = 0;
  let activeSources = new Set();
  let selectedProfile = localStorage.getItem("james_voice_style") || "jarvis";
  let lastError = "";

  const status = (message, state = "idle") => {
    const node = document.getElementById("voiceAvailability");
    if (node) {
      node.textContent = message;
      node.dataset.state = state;
    }
    const badge = document.getElementById("neuralVoiceState");
    if (badge) badge.textContent = state === "ready" ? "MOTOR LOCAL ATIVO" : state === "loading" ? "CARREGANDO MOTOR" : state === "error" ? "VOZ INDISPONÍVEL" : "VOZ NÃO INSTALADA";
  };

  function ensureAudioContext() {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) throw new Error("Este navegador não oferece áudio compatível.");
    if (!audioContext || audioContext.state === "closed") audioContext = new AudioContextClass();
    return audioContext;
  }

  function stop() {
    for (const source of activeSources) {
      try { source.stop(); } catch (_) {}
      try { source.disconnect(); } catch (_) {}
    }
    activeSources.clear();
    nextStartAt = audioContext ? audioContext.currentTime : 0;
    if (engine && typeof engine.cancel === "function") {
      try { engine.cancel(); } catch (_) {}
    }
  }

  async function initialize() {
    if (engine) return engine;
    if (loading) return loading;
    loading = (async () => {
      status("Preparando o motor neural local. O primeiro carregamento baixa aproximadamente 295 MB; o áudio é sintetizado no aparelho.", "loading");
      try {
        if (!wasmFileset) wasmFileset = await FilesetResolver.forAudioTasks(WASM_URL);
        const profile = profiles[selectedProfile] || profiles.jarvis;
        const instance = await TextToSpeech.createFromOptions(wasmFileset, {
          baseOptions: { modelAssetPath: MODEL_URL, delegate: "GPU" },
          defaultVoiceId: profile.voiceId,
          defaultLocale: "pt-BR",
          speakingRate: 1.0
        });
        const voices = await instance.listVoices();
        const required = ["pm_alex", "pm_santa"];
        const available = new Set((voices || []).map(item => item.voiceId));
        if (!required.every(id => available.has(id))) {
          instance.close();
          throw new Error("O modelo foi carregado, mas as vozes masculinas pt-BR esperadas não foram encontradas.");
        }
        engine = instance;
        status("Motor neural Kokoro ativo. Vozes masculinas pt-BR prontas; síntese local, sem a voz padrão do Google.", "ready");
        return engine;
      } catch (error) {
        lastError = error?.message || "Falha ao inicializar o motor de voz.";
        status("Não foi possível carregar a voz neural: " + lastError + " Você ainda pode usar o chat por texto; nenhuma voz do Google será acionada como substituta.", "error");
        throw error;
      } finally {
        loading = null;
      }
    })();
    return loading;
  }

  function schedule(samples, sampleRate, rate) {
    const context = ensureAudioContext();
    const buffer = context.createBuffer(1, samples.length, sampleRate);
    buffer.copyToChannel(samples, 0);
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.playbackRate.value = rate;
    source.connect(context.destination);
    const start = Math.max(context.currentTime + 0.025, nextStartAt);
    source.start(start);
    nextStartAt = start + buffer.duration / rate;
    activeSources.add(source);
    source.onended = () => {
      activeSources.delete(source);
      try { source.disconnect(); } catch (_) {}
    };
  }

  async function speak(text) {
    const content = String(text || "").trim().slice(0, 12000);
    if (!content || !engine || localStorage.getItem("james_voice_enabled") === "false") return false;
    stop();
    const context = ensureAudioContext();
    if (context.state === "suspended") await context.resume();
    const profile = profiles[selectedProfile] || profiles.jarvis;
    status("Preparando fala local…", "ready");
    try {
      const stream = await engine.synthesizeStream(content, { voiceId: profile.voiceId, locale: "pt-BR" });
      for await (const chunk of stream) {
        if (chunk?.samples?.length) schedule(chunk.samples, chunk.sampleRateHz || 24000, profile.rate);
      }
      return true;
    } catch (error) {
      lastError = error?.message || "Falha durante a síntese.";
      status("A síntese local falhou: " + lastError + ". O Jâmes não trocará silenciosamente para a voz do Google.", "error");
      return false;
    }
  }

  function setProfile(profileId) {
    if (!profiles[profileId]) return;
    selectedProfile = profileId;
    localStorage.setItem("james_voice_style", profileId);
    document.querySelectorAll("[data-voice-style]").forEach(button => {
      button.classList.toggle("active", button.dataset.voiceStyle === profileId);
      button.setAttribute("aria-pressed", String(button.dataset.voiceStyle === profileId));
    });
    const label = document.getElementById("holoVoiceName");
    if (label) label.textContent = profileId === "jarvis" ? "JÂMES / CLÁSSICA" : profileId === "patrick" ? "JÂMES / ANALÍTICA" : "JÂMES / INTENSA";
    status(engine ? "Perfil alterado. A próxima fala usará a voz neural selecionada." : "Perfil salvo. Carregue o motor neural para ouvir a voz.", engine ? "ready" : "idle");
  }

  async function loadAndTest() {
    const button = document.getElementById("downloadNeuralVoice");
    if (button) button.disabled = true;
    try {
      await initialize();
      await speak("Olá. Eu sou Jâmes. Meu sistema de voz neural está ativo e pronto para conversar com você em português brasileiro.");
    } catch (_) {
      // The status panel already contains the actionable error.
    } finally {
      if (button) button.disabled = false;
    }
  }

  window.JamesNeuralVoice = {
    initialize,
    speak,
    stop,
    setProfile,
    isReady: () => Boolean(engine),
    getStatus: () => ({ ready: Boolean(engine), loading: Boolean(loading), error: lastError })
  };

  document.addEventListener("DOMContentLoaded", () => {
    setProfile(selectedProfile);
    const button = document.getElementById("downloadNeuralVoice");
    if (button) button.addEventListener("click", loadAndTest);
    const test = document.getElementById("testJamesVoice");
    if (test) test.addEventListener("click", async () => {
      if (!engine) return loadAndTest();
      await speak("Esta é uma amostra da voz neural do Jâmes, processada localmente no seu aparelho.");
    });
    const save = document.getElementById("saveJamesVoice");
    if (save) save.addEventListener("click", () => {
      localStorage.setItem("james_voice_style", selectedProfile);
      status("Perfil vocal salvo neste navegador.", engine ? "ready" : "idle");
    });
    document.querySelectorAll("[data-voice-style]").forEach(button => {
      button.addEventListener("click", () => setProfile(button.dataset.voiceStyle));
    });
    const select = document.getElementById("jamesVoiceSelect");
    if (select) {
      select.innerHTML = '<option value="pm_alex">Alex · Masculina, clara e equilibrada</option><option value="pm_santa">Santa · Masculina, mais encorpada</option>';
      select.value = selectedProfile === "demon" ? "pm_santa" : "pm_alex";
      select.addEventListener("change", () => setProfile(select.value === "pm_santa" ? "demon" : "jarvis"));
    }
    status("Voz neural ainda não carregada. Toque em “Carregar voz neural” para baixar o modelo local (aprox. 295 MB).", "idle");
  });
})();
