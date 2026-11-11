/* JÂMES v2.4 — globe rendering and explicit voice interaction */
(()=>{
 const root=document.getElementById("jamesGlobeCanvas");
 if(root){
  const ctx=root.getContext("2d");
  let width=0,height=0,angle=0,raf=0;
  const resize=()=>{const rect=root.getBoundingClientRect();const dpr=Math.min(window.devicePixelRatio||1,2);width=Math.max(1,rect.width);height=Math.max(1,rect.height);root.width=Math.round(width*dpr);root.height=Math.round(height*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);};
  const points=[];
  for(let lat=-78;lat<=78;lat+=13){for(let lon=0;lon<360;lon+=13){const p=lat*Math.PI/180,l=lon*Math.PI/180;points.push({x:Math.cos(p)*Math.sin(l),y:Math.sin(p),z:Math.cos(p)*Math.cos(l)});}}
  const draw=()=>{if(!ctx)return;ctx.clearRect(0,0,width,height);angle+=.0018;const radius=Math.min(width,height)*.48,cx=width/2,cy=height/2;ctx.fillStyle="#8befff";for(const p of points){const x=p.x*Math.cos(angle)-p.z*Math.sin(angle),z=p.x*Math.sin(angle)+p.z*Math.cos(angle);if(z<-.12)continue;const depth=(z+1)/2,px=cx+x*radius,py=cy+p.y*radius;ctx.globalAlpha=.18+depth*.7;ctx.beginPath();ctx.arc(px,py,.55+depth*1.1,0,Math.PI*2);ctx.fill();}ctx.globalAlpha=1;raf=requestAnimationFrame(draw);};
  resize();draw();window.addEventListener("resize",resize,{passive:true});document.addEventListener("visibilitychange",()=>{if(document.hidden)cancelAnimationFrame(raf);else draw();});
 }
 const mic=document.getElementById("headMic");
 const transcript=document.getElementById("headTranscript");
 const micState=document.getElementById("headMicState");
 const prompt=document.getElementById("prompt");
 const send=document.getElementById("send");
 const Recognition=window.SpeechRecognition||window.webkitSpeechRecognition;
 if(!mic)return;
 if(!Recognition){mic.disabled=true;mic.title="Reconhecimento de voz não suportado neste navegador";if(micState)micState.textContent="Reconhecimento de voz indisponível neste navegador. O microfone permanece desativado.";return;}
 let recognition=null,listening=false;
 const stop=()=>{try{recognition&&recognition.stop();}catch(e){}listening=false;mic.setAttribute("aria-pressed","false");mic.textContent="🎙";if(micState)micState.textContent="Escuta pausada. Toque no microfone para falar novamente.";};
 mic.addEventListener("click",()=>{
  if(listening){stop();return;}
  recognition=new Recognition();recognition.lang="pt-BR";recognition.continuous=false;recognition.interimResults=true;recognition.maxAlternatives=1;
  recognition.onstart=()=>{listening=true;mic.setAttribute("aria-pressed","true");mic.textContent="■";if(micState)micState.textContent="Estou ouvindo. Fale naturalmente; não é necessário usar comandos especiais.";};
  recognition.onresult=event=>{let interim="",finalText="";for(let i=event.resultIndex;i<event.results.length;i++){const text=event.results[i][0].transcript;if(event.results[i].isFinal)finalText+=text;else interim+=text;}if(transcript)transcript.textContent=finalText||interim||"Escutando…";if(finalText.trim()){prompt.value=finalText.trim();if(send)send.click();}};
  recognition.onerror=event=>{if(micState)micState.textContent="Microfone: "+(event.error==="not-allowed"?"permissão negada":event.error==="no-speech"?"nenhuma fala detectada":"falha no reconhecimento")+". Confira a permissão do navegador.";stop();};
  recognition.onend=()=>{if(listening)stop();};
  try{recognition.start();}catch(error){stop();if(micState)micState.textContent="Não foi possível iniciar o microfone. Tente novamente.";}
 });
 window.addEventListener("pagehide",stop);
})();
