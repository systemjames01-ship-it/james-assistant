(() => {
  "use strict";

  const VOZZ_MODULE = "https://cdn.jsdelivr.net/gh/Pedro21062014/vozz@v0.2.7/src/piper.js";
  const VOZZ_ASSETS = "https://cdn.jsdelivr.net/gh/Pedro21062014/vozz@v0.2.7";
  const VOZZ_SYNTH = "https://cdn.jsdelivr.net/gh/Pedro21062014/vozz@v0.2.7/src/sintetizador.js";
  const profiles = {
    jarvis: { label: "Jâmes · Clássica", engine: "formant", voice: "bruno", speed: 1.0, pitch: 1.0, bass: 0.2, clarity: 1.8, lowpass: 9000, highpass: 75 },
    patrick: { label: "Jâmes · Analítica", engine: "piper", speed: 1.035, pitch: 1.0, bass: 1.4, clarity: 0.7, lowpass: 8200, highpass: 65 },
    demon: { label: "Jâmes · Intensa", engine: "piper", speed: 0.96, pitch: 0.965, bass: 3.0, clarity: -0.8, lowpass: 6100, highpass: 55 }
  };

  let engine = null;
  let syntheticEngine = null;
  let syntheticLoading = null;
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
    if (badge) badge.textContent = state === "ready" ? "MOTOR DE VOZ ATIVO" : state === "loading" ? "CARREGANDO VOZ" : state === "error" ? "VOZ INDISPONÍVEL" : "VOZ LOCAL PRONTA";
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

  async function initializeSynthetic() {
    if (syntheticEngine) return syntheticEngine;
    if (syntheticLoading) return syntheticLoading;
    syntheticLoading = (async () => {
      const { Sintetizador } = await import(VOZZ_SYNTH);
      syntheticEngine = new Sintetizador({ voz: "bruno" });
      return syntheticEngine;
    })();
    try { return await syntheticLoading; }
    finally { syntheticLoading = null; }
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
    if (!content || localStorage.getItem("james_voice_enabled") === "false") return false;
    stop();
    const activeRun = runId;
    const context = ensureAudioContext();
    if (context.state === "suspended") await context.resume();
    const baseProfile = profiles[selectedProfile] || profiles.jarvis;
    const userRate = Number(document.getElementById("voiceRate")?.value || 1);
    const userPitch = Number(document.getElementById("voicePitch")?.value || 1);
    const profile = { ...baseProfile, speed: baseProfile.speed * userRate, pitch: baseProfile.pitch * userPitch };

    try {
      if (profile.engine === "formant") {
        const synthesizer = await initializeSynthetic();
        const sentences = content.match(/[^.!?]+[.!?]+|[^.!?]+$/g) || [content];
        status("Voz masculina sintética Jâmes ativa · Bruno · processamento local.", "ready");
        for (const sentence of sentences) {
          if (activeRun !== runId) return false;
          const audio = synthesizer.falar(sentence.trim(), { voz: profile.voice, velocidade: profile.speed, entonacao: 1 });
          await playChunk(audio, profile, activeRun);
        }
        return activeRun === runId;
      }

      if (!engine) {
        status("Este perfil usa a voz neural natural Faber. Abra VOZES e carregue o motor neural (~30 MB). O perfil Jâmes Clássico já funciona sem download.", "idle");
        return false;
      }

      status("Sintetizando resposta neural em português brasileiro…", "ready");
      for await (const part of engine.falarEmFluxo(content, { velocidade: profile.speed, normalizar: true })) {
        if (activeRun !== runId) return false;
        await playChunk(part.audio, profile, activeRun);
      }
      return activeRun === runId;
    } catch (error) {
      lastError = error?.message || "Falha durante a síntese.";
      status("A síntese local falhou: " + lastError + ". Nenhuma voz do Google será usada como substituta.", "error");
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
    status(profileId === "jarvis" ? "Perfil Jâmes Clássico pronto: voz masculina sintética local, sem download." : engine ? "Perfil neural natural atualizado." : "Este perfil usa a voz Faber. Carregue o motor neural (~30 MB) para ouvir.", profileId === "jarvis" || engine ? "ready" : "idle");
  }

  async function loadAndTest() {
    const button = document.getElementById("downloadNeuralVoice");
    if (button) button.disabled = true;
    try {
      const context = ensureAudioContext();
      if (context.state === "suspended") await context.resume();
      await initialize();
      const previousProfile = selectedProfile;
      selectedProfile = "patrick";
      try {
        await speak("Olá. Eu sou Jâmes. Minha voz neural natural em português brasileiro está ativa e pronta para conversar com você.");
      } finally {
        selectedProfile = previousProfile;
        setProfile(previousProfile);
      }
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

    let width = 0, height = 0, phase = 0, lastDraw = 0, raf = 0;
    const pointCount = 112;
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    const points = Array.from({ length: pointCount }, (_, i) => {
      const y = 1 - (i / (pointCount - 1)) * 2;
      const radius = Math.sqrt(1 - y * y);
      const angle = goldenAngle * i;
      return { x: Math.cos(angle) * radius, y, z: Math.sin(angle) * radius };
    });
    const edges = [];
    for (let i = 0; i < points.length; i++) {
      for (let j = i + 1; j < points.length; j++) {
        const a = points[i], b = points[j];
        const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        if (dx * dx + dy * dy + dz * dz < 0.34) edges.push([i, j]);
      }
    }

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
      phase += 0.009;
      ctx.clearRect(0, 0, width, height);

      const active = document.getElementById("holoProjection")?.dataset.state === "live";
      const cx = width * 0.5, cy = height * 0.51;
      const radius = Math.max(24, Math.min(width, height) * (active ? 0.39 : 0.36));
      const rotY = phase, rotX = -0.24 + Math.sin(phase * 0.43) * 0.12;
      const cosY = Math.cos(rotY), sinY = Math.sin(rotY);
      const cosX = Math.cos(rotX), sinX = Math.sin(rotX);
      const projected = points.map(point => {
        const x = point.x * cosY - point.z * sinY;
        const z = point.x * sinY + point.z * cosY;
        const y = point.y * cosX - z * sinX;
        const depth = point.y * sinX + z * cosX;
        const perspective = 2.8 / (2.8 - depth * 0.58);
        return {
          x: cx + x * radius * perspective,
          y: cy + y * radius * perspective,
          depth: (depth + 1) * 0.5
        };
      });

      const aura = ctx.createRadialGradient(cx, cy, radius * 0.08, cx, cy, radius * 1.55);
      aura.addColorStop(0, active ? "rgba(75,226,255,.22)" : "rgba(65,193,235,.14)");
      aura.addColorStop(0.58, "rgba(26,130,190,.08)");
      aura.addColorStop(1, "rgba(8,36,68,0)");
      ctx.fillStyle = aura;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.55, 0, Math.PI * 2);
      ctx.fill();

      ctx.save();
      ctx.strokeStyle = active ? "rgba(112,238,255,.28)" : "rgba(112,213,242,.18)";
      ctx.lineWidth = 1;
      ctx.setLineDash([3, 7]);
      ctx.beginPath();
      ctx.ellipse(cx, cy, radius * 1.16, radius * 0.36, -0.42, 0, Math.PI * 2);
      ctx.stroke();
      ctx.setLineDash([1, 8]);
      ctx.beginPath();
      ctx.ellipse(cx, cy, radius * 1.12, radius * 0.43, 0.62, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      for (const [from, to] of edges) {
        const a = projected[from], b = projected[to];
        const depth = (a.depth + b.depth) * 0.5;
        if (depth < 0.18) continue;
        ctx.beginPath();
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
        ctx.strokeStyle = active
          ? "rgba(105,235,255," + (0.08 + depth * 0.27) + ")"
          : "rgba(103,210,240," + (0.055 + depth * 0.19) + ")";
        ctx.lineWidth = 0.65 + depth * 0.45;
        ctx.stroke();
      }

      for (const point of projected) {
        const size = 0.7 + point.depth * (active ? 2.3 : 1.8);
        ctx.beginPath();
        ctx.arc(point.x, point.y, size, 0, Math.PI * 2);
        ctx.fillStyle = point.depth > 0.68 ? "rgba(222,253,255,.96)" : "rgba(91,224,255," + (0.34 + point.depth * 0.58) + ")";
        ctx.shadowColor = "rgba(71,218,255,.9)";
        ctx.shadowBlur = point.depth > 0.65 ? 9 : 4;
        ctx.fill();
      }
      ctx.shadowBlur = 0;

      const core = ctx.createRadialGradient(cx - radius * 0.08, cy - radius * 0.12, 1, cx, cy, radius * 0.33);
      core.addColorStop(0, "rgba(238,255,255,.9)");
      core.addColorStop(0.18, "rgba(106,236,255,.36)");
      core.addColorStop(0.62, "rgba(32,150,213,.12)");
      core.addColorStop(1, "rgba(32,150,213,0)");
      ctx.fillStyle = core;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.33, 0, Math.PI * 2);
      ctx.fill();

      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.015, 0, Math.PI * 2);
      ctx.strokeStyle = active ? "rgba(143,245,255,.58)" : "rgba(117,221,245,.36)";
      ctx.lineWidth = 1.1;
      ctx.stroke();
    };

    resize();
    let resizeObserver = null;
    if ("ResizeObserver" in window) {
      resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(canvas);
    } else {
      window.addEventListener("resize", resize, { passive: true });
    }
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
    status("Jâmes Clássico (Bruno sintético) já funciona localmente. Para os perfis naturais Patrick e Damon, carregue o modelo neural Faber (~18,7 MB + runtime WASM ~11 MB).", "idle");
  });
})();
