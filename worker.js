/* Jâmes v4 — Gemini fast response path */
const ORIGIN="https://systemjames01-ship-it.github.io";
const MODEL="gemini-3.8-flash";
const REQUEST_TIMEOUT_MS=90000;

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
  return history.slice(-10).map(x=>({
    role:x?.role==="model"?"model":"user",
    parts:[{text:String(x?.text||"").slice(0,3000)}]
  })).filter(x=>x.parts[0].text.trim());
}

async function streamAsk(env,message,history=[],origin=ORIGIN){
  if(!env.GEMINI_API_KEY) return out({reply:"O núcleo de IA ainda não está configurado no Worker.",ok:false,errorCode:"NO_API_KEY"},503,origin);
  const contents=[
    ...cleanHistory(history),
    {role:"user",parts:[{text:message}]}
  ];
  const system="Você é Jâmes, o assistente pessoal de Jamerson Ramos. Responda em português do Brasil por padrão. Seja natural, inteligente, objetivo e útil. Não invente capacidades que ainda não foram implementadas. Quando não souber algo, diga isso claramente.";
  const url="https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":streamGenerateContent?alt=sse";
  try{
    const r=await fetch(url,{
      method:"POST",
      headers:{"Content-Type":"application/json","x-goog-api-key":env.GEMINI_API_KEY},
      body:JSON.stringify({
        systemInstruction:{parts:[{text:system}]},
        contents,
        generationConfig:{thinkingConfig:{thinkingLevel:"medium"}}
      }),
      signal:AbortSignal.timeout(REQUEST_TIMEOUT_MS)
    });
    if(!r.ok){
      const d=await r.json().catch(()=>({}));
      const code=String(d?.error?.status||d?.error?.code||r.status);
      return out({
        reply:"Não consegui responder agora. O serviço de IA retornou "+code+". Tente novamente em instantes.",
        ok:false,errorCode:code,model:MODEL
      },r.status,origin);
    }
    if(!r.body) return out({reply:"O serviço de IA não iniciou o fluxo de resposta.",ok:false,errorCode:"EMPTY_STREAM"},502,origin);
    return new Response(r.body,{
      status:200,
      headers:{
        ...headers(origin),
        "Content-Type":"text/event-stream; charset=utf-8",
        "Cache-Control":"no-cache, no-transform",
        "X-Accel-Buffering":"no"
      }
    });
  }catch(e){
    const timeout=e?.name==="TimeoutError"||e?.name==="AbortError";
    return out({
      reply:timeout?"A conexão com a IA excedeu o limite de 90 segundos. Tente novamente.":"Não consegui conectar ao serviço de IA. Verifique a conexão e tente novamente.",
      ok:false,errorCode:timeout?"AI_TIMEOUT":"NETWORK_ERROR"
    },timeout?504:502,origin);
  }
}

export default {async fetch(request,env){
  const origin=request.headers.get("Origin")||ORIGIN;
  if(request.method==="OPTIONS") return new Response(null,{status:204,headers:headers(origin)});
  if(request.method==="GET") return out({
    status:"online",
    service:"Jâmes AI Worker",
    aiConfigured:!!env.GEMINI_API_KEY,
    model:MODEL,
    capabilities:["text_chat","conversation_context","voice_bridge","vision_bridge"]
  },200,origin);
  if(request.method!=="POST") return out({error:"Método não suportado."},405,origin);
  let b={};
  try{b=await request.json()}catch{return out({error:"JSON inválido."},400,origin)}
  const message=String(b.message||"").trim();
  if(!message) return out({error:"Mensagem vazia."},400,origin);
  return streamAsk(env,message,b.history,origin);
}};
