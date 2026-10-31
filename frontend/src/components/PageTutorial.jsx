"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { buildTourSteps, pageTours } from "@/lib/page-tours.mjs";

export default function PageTutorial(){
 const pathname=usePathname();
 const active=useRef(null),generation=useRef(0),busy=useRef(false),button=useRef(null);
 const [loading,setLoading]=useState(false),[error,setError]=useState("");
 useEffect(()=>{
  generation.current++;busy.current=false;setLoading(false);setError("");
  return ()=>{generation.current++;active.current?.destroy();active.current=null;};
 },[pathname]);
 const start=async()=>{
  if(busy.current||active.current?.isActive())return;
  const request=generation.current;
  busy.current=true;setLoading(true);setError("");
  try{
   const {driver}=await import("driver.js");
   if(request!==generation.current)return;
   const steps=buildTourSteps(pathname,selector=>[...document.querySelectorAll(selector)].find(element=>element.getClientRects().length>0));
   if(!steps.length)return;
   const tour=driver({
    steps,showProgress:true,progressText:"{{current}} de {{total}}",nextBtnText:"Próximo",prevBtnText:"Anterior",doneBtnText:"Concluir",
    popoverClass:"finsys-tour",allowClose:true,allowKeyboardControl:true,disableActiveInteraction:true,
    animate:!window.matchMedia("(prefers-reduced-motion: reduce)").matches,overlayOpacity:0.65,stagePadding:6,
    onPopoverRender:popover=>{popover.closeButton.setAttribute("aria-label","Fechar tutorial");},
    onDestroyed:()=>{active.current=null;if(request===generation.current)button.current?.focus();},
   });
   active.current=tour;tour.drive();
  }catch{if(request===generation.current)setError("Não foi possível abrir o tutorial. Tente novamente.");}
  finally{if(request===generation.current){busy.current=false;setLoading(false);}}
 };
 if(!pageTours[pathname])return null;
 return <div className="page-tutorial-launcher">
  {error&&<p role="alert" className="page-tutorial-error">{error}</p>}
  <button ref={button} className="page-tutorial-button" type="button" onClick={start} disabled={loading} aria-label="Iniciar tutorial desta tela">
   <span aria-hidden="true">?</span>{loading?"Abrindo tutorial…":"Tutorial desta tela"}
  </button>
 </div>;
}
