/* Jâmes v4 — text communication core */
const ORIGIN="https://systemjames01-ship-it.github.io";
const MODELS=["gemini-3.8-flash","gemini-3.6-flash","gemini-3.5-flash-lite"];

const headers=o=>({
  "Access-Control-Allow-Origin":o||ORIGIN,
  "Access-Control-Allow-Headers":"Content-Type",
  "Access-Control-Allow-Methods":"GET,POST,OPTIONS"
});
const out=(v,s,o)=>new Response(JSON.stringify(v),{
  status:s||200,
  headers:{"Content-Type":"application/json; charset=utf-8",...headers(o)}
});

function cleanHistory(history){
  if(!Array.isArray(history)) return [];
  return history.slice(-20).map(x=>({
    role:x?.role==="model"?"model":"user",
    parts:[{text:String(x?.text||"").slice(0,6000)}]
  })).filter(x=>x.parts[0].text.trim());
}

async function ask(env,message,history=[]){
  if(!env.GEMINI_API_KEY) return {text:"O núcleo de IA ainda não está configurado no Worker.",ok:false,code:"NO_API_KEY"};
  const contents=[
    ...cleanHistory(history),
    {role:"user",parts:[{text:message}]}
  ];
  const system="Você é Jâmes, o assistente pessoal de Jamerson Ramos. Responda em português do Brasil por padrão. Seja natural, inteligente, objetivo e útil. Não invente capacidades que ainda não foram implementadas. Quando não souber algo, diga isso claramente.";
  let lastCode="AI_UNAVAILABLE";
  for(const model of MODELS){
    const url="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";
    const body={
      systemInstruction:{parts:[{text:system}]},
      contents,
      generationConfig:{maxOutputTokens:2048,temperature:.7}
    };
    try{
      const r=await fetch(url,{
        method:"POST",
        headers:{"Content-Type":"application/json","x-goog-api-key":env.GEMINI_API_KEY},
        body:JSON.stringify(body)
      });
      const d=await r.json().catch(()=>({}));
      if(r.ok){
        const parts=d?.candidates?.[0]?.content?.parts||[];
        const text=parts.map(p=>p.text||"").filter(Boolean).join("\n").trim();
        if(text) return {text,ok:true,model};
      }
      lastCode=String(d?.error?.status||d?.error?.code||r.status);
      if(r.status===400||r.status===401||r.status===403) break;
    }catch(e){lastCode="NETWORK_ERROR";}
  }
  return {text:"Não consegui responder agora. O núcleo de IA recusou a solicitação ("+lastCode+"). Verifique a chave GEMINI_API_KEY e o acesso ao modelo no Google AI Studio.",ok:false,code:lastCode};
}

export default {async fetch(request,env){
  const origin=request.headers.get("Origin")||ORIGIN;
  if(request.method==="OPTIONS") return new Response(null,{status:204,headers:headers(origin)});
  if(request.method==="GET") return out({
    status:"online",
    service:"Jâmes AI Worker",
    aiConfigured:!!env.GEMINI_API_KEY,
    capabilities:["text_chat","conversation_context","voice_bridge","vision_bridge"]
  },200,origin);
  if(request.method!=="POST") return out({error:"Método não suportado."},405,origin);
  let b={};
  try{b=await request.json()}catch{return out({error:"JSON inválido."},400,origin)}
  const message=String(b.message||"").trim();
  if(!message) return out({error:"Mensagem vazia."},400,origin);
  const r=await ask(env,message,b.history);
  return out({
    reply:r.text,
    ok:r.ok,
    model:r.model||null,
    errorCode:r.ok?null:r.code
  },r.ok?200:503,origin);
}};