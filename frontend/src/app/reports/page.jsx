"use client";
import {useEffect,useState,useCallback} from "react";
import {useRouter} from "next/navigation";
import {authFetch} from "@/lib/api";
import Sidebar from "../components/page";
import "../dashboard/dashboard.css";
import "./reports.css";
const today=()=>new Intl.DateTimeFormat("en-CA",{timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
const money=v=>v==null?"Pendente":new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(v);
const parse=s=>{try{return JSON.parse(s)||{};}catch{return {};}};
function Fields({value}) {
 if(value==null)return <span>?</span>;
 if(typeof value!=="object")return <span>{String(value)}</span>;
 return <dl>{Object.entries(value).map(([k,v])=><div key={k}><dt>{k}</dt><dd><Fields value={v}/></dd></div>)}</dl>;
}
export default function Reports(){
 const [orders,setOrders]=useState([]),[error,setError]=useState(""),[loading,setLoading]=useState(true),[search,setSearch]=useState(""),[selected,setSelected]=useState(null);
 const [date,setDate]=useState(today);
 const router=useRouter();
 const load=useCallback(async()=>{
  try{const r=await authFetch("/olist/vendas/pedidos?data="+encodeURIComponent(date));if(r.status===401){router.push("/login");return;}if(!r.ok)throw Error("Nao foi possivel carregar as vendas.");setOrders(await r.json());setError("");}
  catch(e){setError(e.message);}finally{setLoading(false);}
 },[router,date]);
 useEffect(()=>{load();const t=setInterval(load,15000);return()=>clearInterval(t);},[load]);
 const recalculate=async id=>{
  try{const r=await authFetch("/olist/vendas/pedidos/"+id+"/recalcular",{method:"POST"});if(!r.ok)throw Error("Nao foi possivel recalcular o pedido.");setSelected(await r.json());await load();}
  catch(e){setError(e.message);}
 };
 const rows=orders.filter(o=>[o.number,o.customerName,o.ecommerceName,o.statusDescription].join(" ").toLowerCase().includes(search.toLowerCase()));
 const active=rows.filter(o=>!["cancelado","2"].includes(o.statusCode));
 const ready=active.filter(o=>o.realValue!=null);
 return <div className="dashboard-layout"><Sidebar/><main className="reports-main"><h1>Relatorios de vendas</h1>
 <p>Vendas registradas automaticamente pelo Tiny/Olist. Atualizacao a cada 15 segundos.</p>
 <div className="report-summary glass"><div>Pedidos<strong>{rows.length}</strong></div><div>Valor real calculado<strong>{money(ready.reduce((s,o)=>s+Number(o.realValue),0))}</strong></div><div>Calculos pendentes<strong>{active.length-ready.length}</strong></div></div>
 <p>Valor real = total do pedido sem frete ? custo dos produtos ? taxas do ecommerce ? imposto de venda. Descontos ja incluidos no total; taxa fixa por unidade. Cancelados excluidos do resumo. Configure custos em Produtos, taxas em Ecommerce e imposto em Configuracoes.</p>
 <div className="report-toolbar"><label>Data da venda<input type="date" aria-label="Data da venda" value={date} onChange={e=>{setDate(e.target.value||today());setSelected(null);}}/></label><input aria-label="Buscar vendas" placeholder="Buscar pedido, cliente, ecommerce ou status" value={search} onChange={e=>setSearch(e.target.value)}/><button onClick={load}>Atualizar</button></div>
 {error&&<p role="alert">{error}</p>}{loading?<p>Carregando...</p>:<div className="report-table glass"><table><thead><tr>{["Pedido","Data","Cliente","Ecommerce","Status","Total","Custos","Taxas ecommerce","Imposto","Valor real","Detalhes"].map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map(o=>{const f=parse(o.financialDetails);return <tr key={o.id}><td>{o.number}</td><td>{o.orderDate}</td><td>{o.customerName}</td><td>{o.ecommerceName||"?"}</td><td>{o.statusDescription}</td><td>{money(f.total)}</td><td>{money(f.custoProdutos)}</td><td>{f.taxaPercentual==null?"Pendente":money(Number(f.taxaPercentual)+Number(f.taxaFixa))}</td><td>{money(f.impostoVenda)}</td><td>{money(o.realValue)}{f.pendencia&&<small>{f.pendencia}</small>}</td><td><button onClick={()=>setSelected(o)}>Ver pedido</button></td></tr>;})}</tbody></table>{!rows.length&&<p>Nenhuma venda encontrada.</p>}</div>}
 {selected&&<section className="report-detail glass"><button onClick={()=>setSelected(null)}>Fechar</button><h2>Pedido {selected.number}</h2><button onClick={()=>recalculate(selected.id)}>Recalcular com custos e taxas atuais</button><h3>Calculo do valor real</h3><Fields value={parse(selected.financialDetails)}/><h3>Dados completos</h3><Fields value={parse(selected.orderDetails)}/><h3>Webhook recebido</h3><Fields value={parse(selected.rawPayload)}/></section>}
 </main></div>;
}
