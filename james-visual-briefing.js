(()=> {
 "use strict";
 const panel=document.getElementById("visualBriefing");
 if(!panel)return;
 const image=document.getElementById("visualBriefingImage");
 const caption=document.getElementById("visualBriefingCaption");
 const credit=document.getElementById("visualBriefingCredit");
 const state=document.getElementById("visualBriefingState");
 const closeButton=document.getElementById("visualBriefingClose");
 let items=[],index=0,timer=null,requestId=0;
 const stop=()=>{if(timer){clearInterval(timer);timer=null;}};
 const clean=value=>String(value||"").replace(/<[^>]*>/g," ").replace(/&[^;]+;/g," ").replace(/\s+/g," ").trim();
 const words=value=>String(value||"").toLocaleLowerCase("pt-BR")
  .normalize("NFD").replace(/[\u0300-\u036f]/g,"")
  .replace(/[^a-z0-9 ]/g," ").split(/\s+/)
  .filter(word=>word.length>2&&!new Set(["para","como","sobre","quero","pode","qual","quando","onde","porque","voce","james","jâmes","assistente","fazer","faca","isso","este","esta","mais","muito","tambem","entao","preciso","ajude","analise","explique"]).has(word))
  .slice(0,6);
 function showItem(){
  if(!items.length)return;
  const item=items[index%items.length];
  image.src=item.thumb;
  image.alt=item.title;
  caption.textContent=item.title;
  credit.textContent=[item.license,item.artist].filter(Boolean).join(" · ")||"Wikimedia Commons";
  state.textContent="REFERÊNCIA "+(index+1)+" / "+items.length+" · TROCA A CADA 7 SEGUNDOS";
  index=(index+1)%items.length;
 }
 async function show(userText,assistantText){
  const wordsFound=words(userText);
  const sensitive=/\\b(?:senha|password|cpf|cnpj|cart[aã]o|conta banc[aá]ria|ag[eê]ncia|endereço|telefone|e-?mail|chave pix|token de acesso|api key|diagn[oó]stico pessoal|prontu[aá]rio)\\b/i;
  if(sensitive.test(String(userText||""))){stop();panel.hidden=true;return;}
  const query=wordsFound.join(" ")||words(assistantText).join(" ");
  if(!query)return;
  const request=++requestId;stop();items=[];panel.hidden=false;
  state.textContent="PESQUISANDO REFERÊNCIAS VISUAIS…";
  caption.textContent=query;
  try{
   const url=new URL("https://commons.wikimedia.org/w/api.php");
   Object.entries({action:"query",generator:"search",gsrsearch:query,gsrnamespace:"6",gsrlimit:"8",prop:"imageinfo",iiprop:"url|extmetadata",iiurlwidth:"1000",format:"json",origin:"*"}).forEach(([k,v])=>url.searchParams.set(k,v));
   const response=await fetch(url,{headers:{Accept:"application/json"}});
   if(!response.ok)throw new Error("HTTP "+response.status);
   const data=await response.json();
   if(request!==requestId)return;
   const pages=Object.values(data.query?.pages||{});
   items=pages.map(page=>{
    const info=page.imageinfo?.[0]||{},meta=info.extmetadata||{};
    const thumb=info.thumburl||info.url||"";
    if(!/^https:\/\/(upload\.wikimedia\.org|commons\.wikimedia\.org)\//i.test(thumb))return null;
    return {thumb,title:String(page.title||"Imagem").replace(/^File:/,"").replace(/_/g," "),license:clean(meta.LicenseShortName?.value),artist:clean(meta.Artist?.value)};
   }).filter(Boolean);
   if(!items.length)throw new Error("Nenhuma imagem adequada encontrada");
   index=0;showItem();panel.hidden=false;
   if(items.length>1)timer=setInterval(showItem,7000);
  }catch(error){
   if(request!==requestId)return;
   panel.hidden=false;state.textContent="REFERÊNCIAS VISUAIS INDISPONÍVEIS";
   caption.textContent="Não foi possível localizar imagens públicas para este assunto.";
   credit.textContent="A conversa continua normalmente.";
   image.removeAttribute("src");
  }
 }
 closeButton?.addEventListener("click",()=>{requestId++;stop();panel.hidden=true;});
 window.JamesVisualBriefing={show,stop};
})();