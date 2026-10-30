"use client";
import {useEffect,useState,useCallback} from "react";
import {useRouter} from "next/navigation";
import {authFetch} from "@/lib/api";
import Sidebar from "../components/page";
import OrderDetailModal from "../components/OrderDetailModal";
import "../orders/orders.css";
import "../dashboard/dashboard.css";
import "./reports.css";
const today=()=>new Intl.DateTimeFormat("en-CA",{timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
const money=v=>v==null?"Pendente":new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(v);
const parse=s=>{try{return JSON.parse(s)||{};}catch{return {};}};
export default function Reports(){
 const [orders,setOrders]=useState([]),[error,setError]=useState(""),[loading,setLoading]=useState(true),[search,setSearch]=useState(""),[selected,setSelected]=useState(null);
 const [date,setDate]=useState(today);
 const [page,setPage]=useState(1),[recalculating,setRecalculating]=useState(false);
 const [recalculatingAll,setRecalculatingAll]=useState(false);
 const [bulkMessage,setBulkMessage]=useState("");
 const [bulkError,setBulkError]=useState("");
 const pageSize=15;
 const router=useRouter();
 const load=useCallback(async()=>{
  try{const r=await authFetch("/olist/vendas/pedidos?data="+encodeURIComponent(date));if(r.status===401){router.push("/login");return;}if(!r.ok)throw Error("Nao foi possivel carregar as vendas.");setOrders(await r.json());setError("");}
  catch(e){setError(e.message);}finally{setLoading(false);}
 },[router,date]);
 useEffect(()=>{load();const t=setInterval(load,15000);return()=>clearInterval(t);},[load]);
 const recalculate=async id=>{
  if(recalculating||recalculatingAll)return;
  setRecalculating(true);
  try{const r=await authFetch("/olist/vendas/pedidos/"+id+"/recalcular",{method:"POST"});if(!r.ok)throw Error("Nao foi possivel recalcular o pedido.");const updated=await r.json();setSelected(current=>current?.id===updated.id?updated:current);setOrders(current=>current.map(order=>order.id===updated.id?updated:order));setError("");}
  catch(e){setError(e.message);}finally{setRecalculating(false);}
 };
 const recalculateAll=async()=>{
  if(recalculatingAll||recalculating)return;
  setRecalculatingAll(true);setBulkMessage("");setBulkError("");
  try{
   const response=await authFetch("/olist/vendas/pedidos/recalcular",{method:"POST"});
   if(response.status===401){router.push("/login");return;}
   if(!response.ok)throw Error("Não foi possível recalcular todos os pedidos. Tente novamente.");
   const result=await response.json();
   setBulkMessage(`${result.total} pedido(s) recalculado(s) com os impostos, custos e taxas atuais. ${result.pending} pedido(s) com valores pendentes.`);
   setSelected(null);
   await load();
  }catch(e){setBulkError(e.message);}finally{setRecalculatingAll(false);}
 };
 const rows=orders.filter(o=>[o.number,o.customerName,o.ecommerceName,o.statusDescription].join(" ").toLowerCase().includes(search.toLowerCase()));
 const pageCount=Math.max(1,Math.ceil(rows.length/pageSize));
 const currentPage=Math.min(page,pageCount);
 const offset=(currentPage-1)*pageSize;
 const visibleRows=rows.slice(offset,offset+pageSize);
 useEffect(()=>{setPage(1);},[search,date]);
 useEffect(()=>{setPage(value=>Math.min(value,pageCount));},[pageCount]);
 const active=rows.filter(o=>!["cancelado","2"].includes(o.statusCode));
 const ready=active.filter(o=>o.realValue!=null);
 return <div className="dashboard-layout"><Sidebar/><main className="reports-main"><h1>Relatórios de vendas</h1>
 <p>Vendas registradas automaticamente pelo Tiny/Olist. Atualizacao a cada 15 segundos.</p>
 <div className="report-summary glass"><div>Pedidos<strong>{rows.length}</strong></div><div>Valor real calculado<strong>{money(ready.reduce((s,o)=>s+Number(o.realValue),0))}</strong></div><div>Calculos pendentes<strong>{active.length-ready.length}</strong></div></div>
 <p>Valor real = total do pedido sem frete − custo dos produtos − taxas do ecommerce − imposto de venda. Descontos ja incluidos no total; taxa fixa por unidade. Cancelados excluídos do resumo financeiro. Totais de todas as vendas filtradas. Configure custos em Produtos, taxas em Ecommerce e imposto em Configuracoes.</p>
 <section className="report-bulk-actions glass" style={{padding:20,marginTop:20}}>
 <button type="button" disabled={recalculatingAll||recalculating} onClick={recalculateAll}>{recalculatingAll?"Recalculando todos os pedidos...":"Recalcular todos os pedidos"}</button>
 <p>Atualiza todos os pedidos da sua conta com os impostos, custos e taxas atuais, independentemente da data, busca e página selecionadas.</p>
 {bulkMessage&&<p role="status">{bulkMessage}</p>}
 {bulkError&&<p role="alert">{bulkError}</p>}
 </section>
 <div className="report-toolbar"><label>Data da venda<input type="date" aria-label="Data da venda" value={date} onChange={e=>{setDate(e.target.value||today());setSelected(null);}}/></label><input aria-label="Buscar vendas" placeholder="Buscar pedido, cliente, ecommerce ou status" value={search} onChange={e=>setSearch(e.target.value)}/><button onClick={load}>Atualizar</button></div>
 {error&&!selected&&<p role="alert">{error}</p>}{loading?<p>Carregando...</p>:<div className="report-table glass"><table><thead><tr>{["Pedido","Data","Cliente","Ecommerce","Status","Total","Custos","Taxas ecommerce","Imposto","Valor real","Detalhes"].map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{visibleRows.map(o=>{const f=parse(o.financialDetails);return <tr key={o.id}><td>{o.number}</td><td>{o.orderDate?o.orderDate.split("-").reverse().join("/"):"Sem data"}</td><td>{o.customerName}</td><td>{o.ecommerceName||"Sem informação"}</td><td>{o.statusDescription}</td><td>{money(f.total)}</td><td>{money(f.custoProdutos)}</td><td>{f.taxaPercentual==null||f.taxaFixa==null?"Pendente":money(Number(f.taxaPercentual)+Number(f.taxaFixa))}</td><td>{money(f.impostoVenda)}</td><td>{money(o.realValue)}{f.pendencia&&<small>{f.pendencia}</small>}</td><td><button onClick={()=>{setError("");setSelected(o);}}>Ver detalhes</button></td></tr>;})}</tbody></table>{!rows.length&&<p>Nenhuma venda encontrada.</p>}</div>}

 {!loading&&rows.length>0&&<nav className="orders-pagination glass" aria-label="Paginação do relatório">
 <p role="status">Mostrando {offset+1}–{Math.min(offset+pageSize,rows.length)} de {rows.length} vendas</p>
 <span>{pageSize} vendas por página</span>
 <div className="orders-page-controls">
 <button disabled={currentPage===1} onClick={()=>setPage(1)}>Primeira</button>
 <button disabled={currentPage===1} onClick={()=>setPage(currentPage-1)}>Anterior</button>
 <span>Página {currentPage} de {pageCount}</span>
 <button disabled={currentPage===pageCount} onClick={()=>setPage(currentPage+1)}>Próxima</button>
 <button disabled={currentPage===pageCount} onClick={()=>setPage(pageCount)}>Última</button>
 </div></nav>}
 {selected&&<OrderDetailModal order={selected} onClose={()=>setSelected(null)}>
 <div className="report-detail-actions"><button disabled={recalculating||recalculatingAll} onClick={()=>recalculate(selected.id)}>{recalculating?"Recalculando...":"Recalcular com custos e taxas atuais"}</button>
 {error&&<p role="alert">{error}</p>}</div>
 </OrderDetailModal>}
 </main></div>;
}
