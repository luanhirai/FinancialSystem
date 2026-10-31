"use client";
import { useTheme } from "./ThemeProvider";
export default function ThemeSettings(){
 const {theme,changeTheme,storageError}=useTheme();
 return <section className="content-section glass" data-tour="theme-settings" aria-labelledby="theme-heading">
  <div className="section-header"><div><h2 id="theme-heading">Aparência</h2><p>Escolha o tema do sistema. Sua preferência é salva automaticamente neste navegador.</p></div></div>
  <fieldset className="theme-options"><legend>Tema do sistema</legend>
   {[{value:"light",name:"Claro",description:"Fundo claro e cores suaves."},{value:"dark",name:"Escuro",description:"Fundo escuro para ambientes com pouca luz."}].map(option=><label className="theme-option" key={option.value}>
    <input type="radio" name="system-theme" value={option.value} checked={theme===option.value} onChange={()=>changeTheme(option.value)}/>
    <span className={"theme-preview theme-preview--"+option.value} aria-hidden="true"><span/><span/><span/></span>
    <span><strong>{option.name}</strong><small>{option.description}</small></span>
   </label>)}
  </fieldset>
  {storageError&&<p role="status" className="theme-storage-message">Tema aplicado. O navegador não permitiu salvar a preferência para a próxima visita.</p>}
 </section>;
}
