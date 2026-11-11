import { FilesetResolver, TextToSpeech } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-audio@1.2.2/+esm";

(() => {
  "use strict";

  const GPU_MODEL_URL = "https://huggingface.co/litert-community/Kokoro-82M/resolve/main/kokoro_int8_gpu_fp16.litertlm";
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
        let instance;
        try {
          instance = await TextToSpeech.createFromOptions(wasmFileset, {
            baseOptions: { modelAssetPath: GPU_MODEL_URL, delegate: "GPU" },
            defaultVoiceId: profile.voiceId,
            defaultLocale: "pt-BR",
            speakingRate: 1.0
          });
        } catch (gpuError) {
          status("A aceleração gráfica não está disponível. Reutilizando o mesmo modelo em CPU; a fala pode ficar mais lenta e consumir mais memória.", "loading");
          instance = await TextToSpeech.createFromOptions(wasmFileset, {
            baseOptions: { modelAssetPath: GPU_MODEL_URL, delegate: "CPU" },
            defaultVoiceId: profile.voiceId,
            defaultLocale: "pt-BR",
            speakingRate: 1.0
          });
        }
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
    const userRate = Number(document.getElementById("voiceRate")?.value || 1);
    const userPitch = Number(document.getElementById("voicePitch")?.value || 1);
    const playbackRate = rate * userRate * userPitch;
    source.playbackRate.value = playbackRate;
    source.connect(context.destination);
    const start = Math.max(context.currentTime + 0.025, nextStartAt);
    source.start(start);
    nextStartAt = start + buffer.duration / playbackRate;
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
    const voiceSelect = document.getElementById("jamesVoiceSelect");
    if (voiceSelect) voiceSelect.value = profileId === "demon" ? "pm_santa" : "pm_alex";
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


  function startNeuralCanvas() {
    const canvas = document.getElementById("jamesNeuralCanvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d", { alpha: true });
    if (!ctx) return;
    let width = 0, height = 0, phase = 0, frame = 0, lastDraw = 0, raf = 0;
    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
      width = Math.max(1, Math.round(rect.width * ratio));
      height = Math.max(1, Math.round(rect.height * ratio));
      canvas.width = width;
      canvas.height = height;
    };
    const draw = now => {
      raf = requestAnimationFrame(draw);
      if (document.hidden || now - lastDraw < 42) return;
      lastDraw = now;
      phase += 0.022;
      frame++;
      ctx.clearRect(0, 0, width, height);
      const mid = height * 0.52;
      ctx.strokeStyle = "rgba(128,217,232,.10)";
      ctx.lineWidth = 1;
      for (let i = 1; i < 6; i++) {
        const y = height * i / 6;
        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
      }
      const active = document.getElementById("holoProjection")?.dataset.state === "live";
      const amp = height * (active ? 0.25 : 0.12);
      for (let layer = 0; layer < 4; layer++) {
        ctx.beginPath();
        for (let x = 0; x <= width; x += 3) {
          const t = x / Math.max(1, width);
          const envelope = 0.12 + Math.pow(Math.sin(Math.PI * t), 1.4) * 0.88;
          const y = mid + Math.sin(t * 21 + phase * (1 + layer * .13) + layer) * amp * envelope * (1 - layer * .13)
            + Math.sin(t * 57 - phase * .7 + layer * 2) * amp * .18;
          if (x === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.strokeStyle = layer === 0 ? "rgba(216,183,122,.92)" : `rgba(128,217,232,${.52 - layer * .09})`;
        ctx.lineWidth = layer === 0 ? 1.8 : 1;
        ctx.stroke();
      }
      const scanX = (frame * 1.4) % Math.max(1, width);
      const gradient = ctx.createLinearGradient(scanX - 35, 0, scanX + 35, 0);
      gradient.addColorStop(0, "rgba(128,217,232,0)");
      gradient.addColorStop(.5, "rgba(128,217,232,.12)");
      gradient.addColorStop(1, "rgba(128,217,232,0)");
      ctx.fillStyle = gradient; ctx.fillRect(scanX - 35, 0, 70, height);
    };
    resize();
    if ("ResizeObserver" in window) new ResizeObserver(resize).observe(canvas);
    else window.addEventListener("resize", resize, { passive: true });
    document.addEventListener("visibilitychange", () => {
      if (document.hidden) cancelAnimationFrame(raf);
      else raf = requestAnimationFrame(draw);
    });
    raf = requestAnimationFrame(draw);
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
    startNeuralCanvas();
    const button = document.getElementById("downloadNeuralVoice");
    if (button) button.addEventListener("click", loadAndTest);
    status("Voz neural ainda não carregada. Toque em “Carregar voz neural” para baixar o modelo local (aprox. 295 MB). O navegador pode reutilizar o cache nas próximas sessões.", "idle");
  });;
})();
