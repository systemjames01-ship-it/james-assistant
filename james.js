const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const API=localStorage.getItem("james_api")||"https://james-ai.systemjames01.workers.dev";
let on=false,stream=null,rec=null,voiceMode=false,recognitionActive=false,restartTimer=null,startedAt=null,session=localStorage.getItem("james_session")||("james_"+crypto.randomUUID());
localStorage.setItem("james_session",session);
const msgs=$("#messages");
const CHAT_KEY="james_chat_v4";
let chat=JSON.parse(localStorage.getItem(CHAT_KEY)||"[]");
let busy=false;
function add(t,w="bot",save=true){
  const d=document.createElement("div"); d.className="msg "+w; d.textContent=t;
  msgs.appendChild(d); msgs.scrollTop=msgs.scrollHeight;
  if(save){chat.push({role:w==="user"?"user":"model",text:t});chat=chat.slice(-40);localStorage.setItem(CHAT_KEY,JSON.stringify(chat))}
}
function renderChat(){
  msgs.innerHTML="";
  if(!chat.length){add("Jâmes 4.0 conectado ao canal de comunicação. Como posso ajudar?", "bot", false);return}
  chat.forEach(x=>add(x.text,x.role==="user"?"user":"bot",false));
}
renderChat();
function speak(t){return new Promise(resolve=>{if(!("speechSynthesis"in window)){resolve();return}speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(t);u.lang="pt-BR";u.rate=.98;u.onend=resolve;u.onerror=resolve;speechSynthesis.speak(u)})}
function stopListening(){clearTimeout(restartTimer);if(rec){const old=rec;rec=null;try{old.onend=null;old.stop()}catch{}}recognitionActive=false}
function scheduleListening(delay=700){clearTimeout(restartTimer);if(voiceMode&&!busy&&on)restartTimer=setTimeout(startListening,delay)}
function startListening(){if(!voiceMode||busy||!on||recognitionActive)return;const R=window.SpeechRecognition||window.webkitSpeechRecognition;if(!R){voiceMode=false;$("#liveText").textContent="Reconhecimento de voz indisponível neste navegador.";$("#voiceModeLabel").textContent="VOICE UNAVAILABLE";return}try{const instance=new R();rec=instance;instance.lang="pt-BR";instance.interimResults=false;instance.continuous=false;instance.maxAlternatives=1;instance.onstart=()=>{recognitionActive=true;$("#liveText").textContent="Estou ouvindo. Pode falar.";$("#avatarState").textContent="OUVINDO";$("#mVoice").textContent="OUVINDO";$("#voiceModeLabel").textContent="MICROFONE ATIVO"};instance.onresult=e=>{const heard=e.results&&e.results[0]&&e.results[0][0]?e.results[0][0].transcript.trim():"";if(!heard)return;if(heard.toLowerCase().includes("encerrar conversa")){voiceMode=false;stopListening();$("#liveText").textContent="Conversa por voz encerrada.";$("#voiceModeLabel").textContent="VOICE STANDBY";$("#listen").querySelector("b").textContent="INICIAR CONVERSA";return}send(heard)};instance.onerror=e=>{if(e.error==="not-allowed"||e.error==="service-not-allowed"){voiceMode=false;$("#liveText").textContent="Permissão de microfone negada. Libere o microfone no Chrome.";$("#voiceModeLabel").textContent="MICROFONE BLOQUEADO"}};instance.onend=()=>{recognitionActive=false;if(rec===instance)rec=null;if(voiceMode&&!busy&&!speechSynthesis?.speaking)scheduleListening(700)};instance.start()}catch(e){recognitionActive=false;scheduleListening(1400)}}
function setState(v){$("#systemState").textContent=v}
async function health(){try{const r=await fetch(API,{cache:"no-store"}),x=await r.json();$("#networkState").textContent=x.status==="online"?(x.aiConfigured?"AI LINK / ONLINE":"WORKER ONLINE / AI SEM CHAVE"):"LINK / ERROR";$("#apiState").textContent=x.aiConfigured?"online":"SEM CHAVE";$("#mMemory").textContent=x.databaseConfigured?"D1":"LOCAL";$("#net").textContent="ONLINE";$("#latency").textContent="READY"}catch{$("#networkState").textContent="LINK / OFFLINE";$("#apiState").textContent="offline";$("#net").textContent="OFF";$("#latency").textContent="—"}}
async function perms(){if(!navigator.mediaDevices?.getUserMedia){add("Este navegador não expõe sensores de mídia.");return false}try{stream=await navigator.mediaDevices.getUserMedia({audio:true,video:true});$("#mic").textContent="authorized";$("#cam").textContent="authorized";$("#mVoice").textContent="ON";$("#mVision").textContent="ON";$("#per").textContent="ACTIVE";$("#camera").srcObject=stream;return true}catch{$("#mic").textContent="blocked";$("#cam").textContent="blocked";add("Microfone/câmera não autorizados. Você pode liberar as permissões nas configurações do navegador.");return false}}
async function send(t){
  t=String(t||"").trim(); if(!t||busy)return;
  add(t,"user"); busy=true;
  const button=$("#form button"); button.disabled=true; button.innerHTML="PROCESSANDO <span>◌</span>";
  setState("CORE / PROCESSING"); $("#liveText").textContent="Jâmes está processando…";
  const started=performance.now();
  // A mensagem atual já foi adicionada à interface; envie apenas o contexto anterior.
  const history=chat.slice(0,-1).slice(-20).map(x=>({role:x.role,text:x.text}));
  const typing=document.createElement("div"); typing.className="msg bot typing"; typing.textContent="Jâmes está pensando…"; msgs.appendChild(typing); msgs.scrollTop=msgs.scrollHeight;
  let responseData={};
  try{
    const r=await fetch(API,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({message:t,sessionId:session,mode:"text",history})});
    responseData=await r.json().catch(()=>({}));
    $("#latency").textContent=Math.round(performance.now()-started)+"ms";
    typing.remove();
    if(!r.ok) throw Error(responseData.reply||responseData.error||"Falha no núcleo (HTTP "+r.status+").");
    if(typeof responseData.reply!=="string"||!responseData.reply.trim()) throw Error("O núcleo respondeu sem conteúdo.");
    add(responseData.reply);
    $("#liveText").textContent="Resposta recebida.";
    if(on){if(voiceMode)await speak(responseData.reply);else speak(responseData.reply);}
  }catch(e){
    typing.remove();
    const code=responseData.errorCode||responseData.code||"NETWORK_OR_SERVER_ERROR";
    add("Falha no núcleo de IA: "+(e?.message||"erro de comunicação")+". Código: "+code+".","bot");
    $("#liveText").textContent="Falha no link neural.";
  }finally{
    busy=false; button.disabled=false; button.innerHTML="ENVIAR <span>↗</span>";
    setState(on?"CORE / ONLINE":"CORE / STANDBY");if(voiceMode){$("#avatarState").textContent="OUVINDO";scheduleListening(850);}
  }
}
async function turnOn(){on=true;startedAt=Date.now();$("#power").classList.add("on");$("#powerText").textContent="ONLINE";$("#coreStatus").textContent="ONLINE";$("#coreMode").textContent="ACTIVE";$("#coreMessage").textContent="Interface neural iniciada. Sensores aguardando autorização.";setState("CORE / ONLINE");await health();speak("Jâmes online. Estou pronto para conversar.");$("#liveText").textContent="Link neural estabelecido."}
function turnOff(){on=false;voiceMode=false;stopListening();$("#power").classList.remove("on");$("#powerText").textContent="STANDBY";$("#coreStatus").textContent="STANDBY";$("#coreMode").textContent="DORMANT";$("#coreMessage").textContent="O núcleo está em repouso. Ligue o sistema para iniciar a interface.";setState("CORE / STANDBY");$("#per").textContent="STANDBY";if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}$("#mVoice").textContent="OFF";$("#mVision").textContent="OFF";$("#liveText").textContent="Jâmes aguardando comando.";speechSynthesis?.cancel()}
$("#power").onclick=()=>on?turnOff():turnOn();
$("#permBtn").onclick=perms;
$("#quickVision").onclick=async()=>{if(!on){add("Ative o núcleo antes de usar Vision.");return}$("#visualState").textContent="SCANNING";$("#visualStage").innerHTML='<div class="visual-orb"></div><span>SCANNING ENVIRONMENT</span>';$("#liveText").textContent="Jâmes analisando contexto visual…";setTimeout(()=>{$("#visualState").textContent="READY";$("#visualStage").innerHTML='<div class="visual-orb"></div><span>CONTEXT READY</span>';$("#liveText").textContent="Contexto visual pronto."},1800)};
$$(".tab").forEach(b=>b.onclick=()=>{$$(".tab").forEach(x=>x.classList.remove("active"));$$(".view").forEach(x=>x.classList.remove("active"));b.classList.add("active");$("#"+b.dataset.tab).classList.add("active")});
$("#form").onsubmit=e=>{e.preventDefault();const v=$("#input").value.trim();$("#input").value="";send(v)};
$("#input").addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();$("#form").requestSubmit()}});
$("#listen").onclick=()=>{if(!on){$("#liveText").textContent="Ligue o Jâmes primeiro.";return}if(voiceMode){voiceMode=false;stopListening();speechSynthesis?.cancel();$("#listen").querySelector("b").textContent="INICIAR CONVERSA";$("#voiceModeLabel").textContent="VOICE STANDBY";$("#mVoice").textContent="OFF";$("#liveText").textContent="Conversa por voz pausada.";$("#avatarState").textContent="EM ESPERA";return}const R=window.SpeechRecognition||window.webkitSpeechRecognition;if(!R){$("#liveText").textContent="Reconhecimento de voz indisponível neste navegador.";return}voiceMode=true;$("#listen").querySelector("b").textContent="ENCERRAR CONVERSA";$("#voiceModeLabel").textContent="INICIANDO VOZ";$("#mVoice").textContent="ON";$("#liveText").textContent="Conversa contínua ativada. Pode falar.";startListening()};
$("#newChat").onclick=()=>{
  session="james_"+crypto.randomUUID(); localStorage.setItem("james_session",session);
  chat=[]; localStorage.setItem(CHAT_KEY,"[]"); renderChat();
  add("Nova sessão neural criada. Estou pronto.", "bot");
};
$("#clear").onclick=()=>{localStorage.removeItem("james_session");location.reload()};
$$(".dial").forEach(b=>b.onclick=()=>{const a=b.dataset.action;const labels={zoom:"ZOOM MATRIX",scan:"HOLOGRAPHIC SCAN",rotate:"ORBIT ROTATION",focus:"NEURAL FOCUS"};$("#consoleReadout").textContent=labels[a]+" / ACTIVE";setState(labels[a]);setTimeout(()=>setState(on?"CORE / ONLINE":"CORE / STANDBY"),1200)});
setInterval(()=>{$("#clock").textContent=new Date().toLocaleTimeString("pt-BR");if(startedAt&&on){const s=Math.floor((Date.now()-startedAt)/1000);$("#uptime").textContent=[Math.floor(s/3600),Math.floor(s/60)%60,s%60].map(v=>String(v).padStart(2,"0")).join(":")}},1000);
health();