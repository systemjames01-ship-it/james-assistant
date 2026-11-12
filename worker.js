/* Jâmes v3 production core — validated after regression workflow. */
const ORIGIN="https://systemjames01-ship-it.github.io";
const MODELS=["gemini-3.8-flash","gemini-3.5-flash-lite","gemini-2.5-flash-lite"];
const headers=o=>({"Access-Control-Allow-Origin":o||ORIGIN,"Access-Control-Allow-Headers":"Content-Type","Access-Control-Allow-Methods":"GET,POST,OPTIONS"});
const out=(v,s,o)=>new Response(JSON.stringify(v),{status:s||200,headers:{"Content-Type":"application/json",...headers(o)}});

async function ask(env,message){
  if(!env.GEMINI_API_KEY)return {text:"O núcleo de IA ainda não está configurado no Worker.",ok:false};
  for(const model of MODELS){
    const url="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent?key="+encodeURIComponent(env.GEMINI_API_KEY);
    const body={contents:[{role:"user",parts:[{text:"Você é Jâmes, IA pessoal de Jamerson Ramos. Seja natural, útil e transparente. "+message}]}],generationConfig:{maxOutputTokens:4096}};
    try{
      const r=await fetch(url,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
      const d=await r.json();
      if(r.ok){
        const parts=d?.candidates?.[0]?.content?.parts||[];
        return {text:parts.map(p=>p.text||"").filter(Boolean).join("\n")||"Sem resposta.",ok:true};
      }
    }catch{}
  }
  return {text:"O provedor de IA está temporariamente indisponível. Tente novamente em instantes.",ok:false};
}
export default {async fetch(request,env){
  const origin=request.headers.get("Origin")||ORIGIN;
  if(request.method==="OPTIONS")return new Response(null,{status:204,headers:headers(origin)});
  const u=new URL(request.url);
  if(request.method==="GET")return out({status:"online",service:"Jâmes AI Worker",aiConfigured:!!env.GEMINI_API_KEY,databaseConfigured:!!env.DB,capabilities:["unified_context","voice","vision","web_search","memory","device_actions"]},200,origin);
  if(request.method!=="POST")return out({error:"Método não suportado."},405,origin);
  let b={};try{b=await request.json()}catch{return out({error:"JSON inválido."},400,origin)}
  const r=await ask(env,String(b.message||""));
  return out({reply:r.text,aiConfigured:!!env.GEMINI_API_KEY},r.ok?200:503,origin);
}};
