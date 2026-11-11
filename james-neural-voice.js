(() => {
  "use strict";

  const VOZZ_MODULE = "https://cdn.jsdelivr.net/gh/Pedro21062014/vozz@v0.2.7/src/piper.js";
  const VOZZ_ASSETS = "https://cdn.jsdelivr.net/gh/Pedro21062014/vozz@v0.2.7";
  const profiles = {
    jarvis: { label: "Jâmes · Clássica", speed: 1.0, pitch: 1.0, bass: 0.2, clarity: 1.8, lowpass: 10800, highpass: 75 },
    patrick: { label: "Jâmes · Analítica", speed: 1.035, pitch: 1.0, bass: 1.4, clarity: 0.7, lowpass: 8200, highpass: 65 },
    demon: { label: "Jâmes · Intensa", speed: 0.96, pitch: 0.965, bass: 3.0, clarity: -0.8, lowpass: 6100, highpass: 55 }
  };

  let engine = null;
  let loading = null;
  let audioContext = null;
  let activeSources = new Set();
  let runId = 0;
  let selectedProfile = localStorage.getItem("james_voice_style") || "jarvis";
  let lastError = "";

  const status = (message, state = "idle") => {
    const node = document.getElementById("voiceAvailability");
    if (node) {
      node.textContent = message;
      node.dataset.state = state;
    }
    const badge = document.getElementById("neuralVoiceState");
    if (badge) badge.textContent = state === "ready" ? "MOTOR NEURAL ATIVO" : state === "loading" ? "CARREGANDO VOZ" : state === "error" ? "VOZ INDISPONÍVEL" : "VOZ NÃO INSTALADA";
  };

  function ensureAudioContext() {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) throw new Error("Este navegador não oferece áudio compatível.");
    if (!audioContext || audioContext.state === "closed") audioContext = new AudioContextClass();
    return audioContext;
  }

  function stop() {
    runId++;
    for (const source of activeSources) {
      try { source.stop(); } catch (_) {}
      try { source.disconnect(); } catch (_) {}
    }
    activeSources.clear();
  }

  async function initialize() {
    if (engine) return engine;
    if (loading) return loading;
    loading = (async () => {
      status("Preparando o motor Piper neural pt-BR. Primeiro carregamento: modelo de aproximadamente 18,7 MB e runtime WASM de aproximadamente 11 MB. O áudio será sintetizado no aparelho.", "loading");
      try {
        const { Piper } = await import(VOZZ_MODULE);
        const instance = await Piper.carregar({
          cdn: VOZZ_ASSETS,
          dispositivo: "auto",
          cache: true,
          aoProgredir: progress => {
            if (progress.status === "baixando" && progress.total > 0) {
              const pct = Math.round(progress.progresso * 100);
              status("Baixando " + (progress.arquivo === "modelo" ? "modelo de voz" : "configuração") + ": " + pct + "% · " + (progress.arquivo === "modelo" ? "18,7 MB" : "arquivo leve"), "loading");
            } else if (progress.status === "cache") {
              status("Arquivo de voz encontrado no cache local.", "loading");
            } else if (progress.status === "iniciando") {
              status("Inicializando o motor neural no aparelho…", "loading");
            }
          }
        });
        engine = instance;
        status("Piper neural pt-BR pronto. Voz masculina Faber, processamento local e cache do modelo ativado.", "ready");
        return engine;
      } catch (error) {
        lastError = error?.message || "Falha ao inicializar o motor neural.";
        status("Não foi possível carregar a voz neural: " + lastError + ". O Jâmes não recorrerá à voz feminina padrão do Google.", "error");
        throw error;
      } finally {
        loading = null;
      }
    })();
    return loading;
  }

  function playChunk(audio, profile, activeRun) {
    return new Promise((resolve, reject) => {
      if (activeRun !== runId) return resolve();
      const context = ensureAudioContext();
      const samples = audio?.amostras;
      const sampleRate = audio?.taxa || 24000;
      if (!samples?.length) return resolve();
      const buffer = context.createBuffer(1, samples.length, sampleRate);
      buffer.copyToChannel(samples, 0);
      const source = context.createBufferSource();
      source.buffer = buffer;
      source.playbackRate.value = profile.pitch;

      const highpass = context.createBiquadFilter();
      highpass.type = "highpass";
      highpass.frequency.value = profile.highpass;

      const bass = context.createBiquadFilter();
      bass.type = "lowshelf";
      bass.frequency.value = 180;
      bass.gain.value = profile.bass;

      const presence = context.createBiquadFilter();
      presence.type = "peaking";
      presence.frequency.value = 2600;
      presence.Q.value = 0.8;
      presence.gain.value = profile.clarity;

      const lowpass = context.createBiquadFilter();
      lowpass.type = "lowpass";
      lowpass.frequency.value = profile.lowpass;

      const compressor = context.createDynamicsCompressor();
      compressor.threshold.value = -22;
      compressor.knee.value = 16;
      compressor.ratio.value = 2.2;
      compressor.attack.value = 0.008;
      compressor.release.value = 0.16;

      source.connect(highpass);
      highpass.connect(bass);
      bass.connect(presence);
      presence.connect(lowpass);
      lowpass.connect(compressor);
      compressor.connect(context.destination);

      activeSources.add(source);
      source.onended = () => {
        activeSources.delete(source);
        try { source.disconnect(); highpass.disconnect(); bass.disconnect(); presence.disconnect(); lowpass.disconnect(); compressor.disconnect(); } catch (_) {}
        resolve();
      };
      source.onerror = reject;
      try { source.start(); } catch (error) { reject(error); }
    });
  }

  async function speak(text) {
    const content = String(text || "").trim().slice(0, 12000);
    if (!content || !engine || localStorage.getItem("james_voice_enabled") === "false") return false;
    stop();
    const activeRun = runId;
    const context = ensureAudioContext();
    if (context.state === "suspended") await context.resume();
    const profile = profiles[selectedProfile] || profiles.jarvis;
    status("Sintetizando resposta em português brasileiro…", "ready");
    try {
      for await (const part of engine.falarEmFluxo(content, { velocidade: profile.speed, normalizar: true })) {
        if (activeRun !== runId) return false;
        await playChunk(part.audio, profile, activeRun);
      }
      return activeRun === runId;
    } catch (error) {
      lastError = error?.message || "Falha durante a síntese.";
      status("A síntese neural falhou: " + lastError + ". Nenhuma voz do Google será usada como substituta.", "error");
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
    status(engine ? "Perfil de interpretação atualizado." : "Perfil salvo. Carregue a voz neural para ouvir.", engine ? "ready" : "idle");
  }

  async function loadAndTest() {
    const button = document.getElementById("downloadNeuralVoice");
    if (button) button.disabled = true;
    try {
      const context = ensureAudioContext();
      if (context.state === "suspended") await context.resume();
      await initialize();
      await speak("Olá. Eu sou Jâmes. Meu sistema de voz neural em português brasileiro está ativo e pronto para conversar com você.");
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
    let width = 0, height = 0, phase = 0, frame = 0, lastDraw = 0, raf = 0, resizeObserver = null;
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
    if ("ResizeObserver" in window) { resizeObserver = new ResizeObserver(resize); resizeObserver.observe(canvas); }
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
    status("Voz neural ainda não carregada. Toque em “Carregar voz neural” para baixar o modelo Faber pt-BR (18,7 MB) e o runtime de áudio (~11 MB). Os arquivos ficam no cache do navegador.", "idle");
  });
})();
