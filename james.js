const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const API=localStorage.getItem("james_api")||"https://james-ai.systemjames01.workers.dev";
let on=false,stream=null,rec=null,startedAt=null,session=localStorage.getItem("james_session")||("james_"+crypto.randomUUID());
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
function speak(t){if(!("speechSynthesis"in window))return;speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(t);u.lang="pt-BR";u.rate=.98;speechSynthesis.speak(u)}
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
    if(on)speak(responseData.reply);
  }catch(e){
    typing.remove();
    const code=responseData.errorCode||responseData.code||"NETWORK_OR_SERVER_ERROR";
    add("Falha no núcleo de IA: "+(e?.message||"erro de comunicação")+". Código: "+code+".","bot");
    $("#liveText").textContent="Falha no link neural.";
  }finally{
    busy=false; button.disabled=false; button.innerHTML="ENVIAR <span>↗</span>";
    setState(on?"CORE / ONLINE":"CORE / STANDBY");
  }
}
async function turnOn(){on=true;startedAt=Date.now();$("#power").classList.add("on");$("#powerText").textContent="ONLINE";$("#coreStatus").textContent="ONLINE";$("#coreMode").textContent="ACTIVE";$("#coreMessage").textContent="Interface neural iniciada. Sensores aguardando autorização.";setState("CORE / ONLINE");await perms();await health();speak("Jâmes online. Núcleo neural ativo.");$("#liveText").textContent="Link neural estabelecido."}
function turnOff(){on=false;$("#power").classList.remove("on");$("#powerText").textContent="STANDBY";$("#coreStatus").textContent="STANDBY";$("#coreMode").textContent="DORMANT";$("#coreMessage").textContent="O núcleo está em repouso. Ligue o sistema para iniciar a interface.";setState("CORE / STANDBY");$("#per").textContent="STANDBY";if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}$("#mVoice").textContent="OFF";$("#mVision").textContent="OFF";$("#liveText").textContent="Jâmes aguardando comando.";speechSynthesis?.cancel()}
$("#power").onclick=()=>on?turnOff():turnOn();
$("#permBtn").onclick=perms;
$("#quickVision").onclick=async()=>{if(!on){add("Ative o núcleo antes de usar Vision.");return}$("#visualState").textContent="SCANNING";$("#visualStage").innerHTML='<div class="visual-orb"></div><span>SCANNING ENVIRONMENT</span>';$("#liveText").textContent="Jâmes analisando contexto visual…";setTimeout(()=>{$("#visualState").textContent="READY";$("#visualStage").innerHTML='<div class="visual-orb"></div><span>CONTEXT READY</span>';$("#liveText").textContent="Contexto visual pronto."},1800)};
$$(".tab").forEach(b=>b.onclick=()=>{$$(".tab").forEach(x=>x.classList.remove("active"));$$(".view").forEach(x=>x.classList.remove("active"));b.classList.add("active");$("#"+b.dataset.tab).classList.add("active")});
$("#form").onsubmit=e=>{e.preventDefault();const v=$("#input").value.trim();$("#input").value="";send(v)};
$("#input").addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();$("#form").requestSubmit()}});
$("#listen").onclick=()=>{if(!on){add("Ative o Jâmes primeiro.");return}const R=window.SpeechRecognition||window.webkitSpeechRecognition;if(!R){add("Este navegador não oferece reconhecimento de voz.");return}if(rec){try{rec.stop()}catch{}rec=null;return}rec=new R();rec.lang="pt-BR";rec.interimResults=true;rec.continuous=false;rec.onstart=()=>{$("#liveText").textContent="Ouvindo…";$("#mVoice").textContent="LISTEN"};rec.onresult=e=>{$("#liveText").textContent=e.results[e.results.length-1][0].transcript};rec.onend=()=>{const t=$("#liveText").textContent;if(t&&t!=="Ouvindo…")send(t);$("#mVoice").textContent="ON";rec=null};rec.start()};
$("#newChat").onclick=()=>{
  session="james_"+crypto.randomUUID(); localStorage.setItem("james_session",session);
  chat=[]; localStorage.setItem(CHAT_KEY,"[]"); renderChat();
  add("Nova sessão neural criada. Estou pronto.", "bot");
};
$("#clear").onclick=()=>{localStorage.removeItem("james_session");location.reload()};
$$(".dial").forEach(b=>b.onclick=()=>{const a=b.dataset.action;const labels={zoom:"ZOOM MATRIX",scan:"HOLOGRAPHIC SCAN",rotate:"ORBIT ROTATION",focus:"NEURAL FOCUS"};$("#consoleReadout").textContent=labels[a]+" / ACTIVE";setState(labels[a]);setTimeout(()=>setState(on?"CORE / ONLINE":"CORE / STANDBY"),1200)});
setInterval(()=>{$("#clock").textContent=new Date().toLocaleTimeString("pt-BR");if(startedAt&&on){const s=Math.floor((Date.now()-startedAt)/1000);$("#uptime").textContent=[Math.floor(s/3600),Math.floor(s/60)%60,s%60].map(v=>String(v).padStart(2,"0")).join(":")}},1000);
health();