"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { authFetch } from "@/lib/api";

const today=()=>new Intl.DateTimeFormat("en-CA",{timeZone:"America/Sao_Paulo",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
const money=value=>value==null?"Pendente":new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(value);
const quantity=value=>value==null?"Pendente":new Intl.NumberFormat("pt-BR",{maximumFractionDigits:6}).format(value);

function SummaryTable({title,groups}) {
  return <section><h3>{title}</h3><div className="report-table glass"><table>
    <thead><tr>{["Nome","Pedidos","Quantidade","Faturamento sem frete","Custos","Taxas","Impostos","Lucro","Pedidos pendentes"].map(label=><th key={label} scope="col">{label}</th>)}</tr></thead>
    <tbody>{groups.map(group=><tr key={group.id}><td>{group.name}</td><td>{group.totals.orders}</td><td>{quantity(group.totals.quantity)}</td><td>{money(group.totals.revenue)}</td><td>{money(group.totals.cost)}</td><td>{money(group.totals.fees)}</td><td>{money(group.totals.tax)}</td><td>{money(group.totals.profit)}{group.totals.pending>0&&<small>Valor parcial</small>}</td><td>{group.totals.pending}</td></tr>)}</tbody>
  </table>{!groups.length&&<p>Nenhuma venda encontrada.</p>}</div></section>;
}

export default function FinancialReports({refreshToken}) {
  const router=useRouter();
  const [start,setStart]=useState(today),[end,setEnd]=useState(today);
  const [product,setProduct]=useState(""),[channel,setChannel]=useState("");
  const [report,setReport]=useState(null),[options,setOptions]=useState({products:[],channels:[]});
  const [loading,setLoading]=useState(true),[exporting,setExporting]=useState(false),[error,setError]=useState("");
  const [page,setPage]=useState(1);
  const [revision,setRevision]=useState(0);
  const query=useMemo(()=>new URLSearchParams({dataInicial:start,dataFinal:end,produtoId:product,ecommerce:channel}).toString(),[start,end,product,channel]);
  useEffect(()=>{
    const controller=new AbortController();
    setReport(null);setError("");setPage(1);
    if(!start||!end||start>end){setLoading(false);setError("Informe um período válido.");return()=>controller.abort();}
    setLoading(true);
    (async()=>{
      try{
        const response=await authFetch("/olist/relatorios/financeiro?"+query,{signal:controller.signal});
        if(response.status===401){router.push("/login");return;}
        if(!response.ok)throw Error("Não foi possível carregar o relatório financeiro.");
        const data=await response.json();
        if(controller.signal.aborted)return;
        setReport(data);setOptions({products:data.productOptions,channels:data.channelOptions});
      }catch(exception){if(!controller.signal.aborted)setError(exception.message);}
      finally{if(!controller.signal.aborted)setLoading(false);}
    })();
    return()=>controller.abort();
  },[query,start,end,refreshToken,revision,router]);
  const exportExcel=async()=>{
    if(!report||loading||exporting)return;
    setExporting(true);setError("");
    try{
      const response=await authFetch("/olist/relatorios/financeiro/excel?"+query);
      if(response.status===401){router.push("/login");return;}
      if(!response.ok)throw Error("Não foi possível exportar o Excel.");
      const url=URL.createObjectURL(await response.blob());
      const link=document.createElement("a");link.href=url;link.download=`financeiro-${start}-${end}.xlsx`;
      document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    }catch(exception){setError(exception.message);}finally{setExporting(false);}
  };
  const count=report?.lines.length||0;
  const pageCount=Math.max(1,Math.ceil(count/25));
  return <section className="financial-reports" aria-labelledby="financial-reports-title">
    <h2 id="financial-reports-title">Relatório financeiro consolidado</h2>
    <p>Consulte vendas por produto e compare o lucro dos canais. Os filtros abaixo se aplicam a este relatório e ao Excel.</p>
    <div className="report-toolbar">
      <label>De<input type="date" value={start} onChange={event=>setStart(event.target.value)}/></label>
      <label>Até<input type="date" value={end} onChange={event=>setEnd(event.target.value)}/></label>
      <label>Produto<select value={product} onChange={event=>setProduct(event.target.value)}><option value="">Todos os produtos</option>{options.products.map(option=><option key={option.id} value={option.id}>{option.name} (Tiny {option.id})</option>)}</select></label>
      <label>Ecommerce<select value={channel} onChange={event=>setChannel(event.target.value)}><option value="">Todos os canais</option>{options.channels.map(name=><option key={name} value={name}>{name}</option>)}</select></label>
      <button type="button" disabled={loading} onClick={()=>setRevision(value=>value+1)}>Atualizar relatório</button>
      <button type="button" disabled={loading||!report||exporting} onClick={exportExcel}>{exporting?"Exportando...":"Exportar Excel (.xlsx)"}</button>
    </div>
    {error&&<p role="alert">{error}</p>}
    {loading&&<p role="status">Carregando relatório financeiro...</p>}
    {report&&<>
      <div className="report-summary glass"><div>Pedidos<strong>{report.totals.orders}</strong></div><div>Quantidade vendida<strong>{quantity(report.totals.quantity)}</strong></div><div>Faturamento sem frete<strong>{money(report.totals.revenue)}</strong></div><div>Custos<strong>{money(report.totals.cost)}</strong></div><div>Taxas<strong>{money(report.totals.fees)}</strong></div><div>Impostos<strong>{money(report.totals.tax)}</strong></div><div>Lucro calculado<strong>{money(report.totals.profit)}</strong></div></div>
      <p>{report.cancelled} pedido(s) cancelado(s) excluído(s) no período. {report.totals.pending} pedido(s) com cálculo pendente. Totais com pendências são parciais. Valores por produto distribuem faturamento, impostos e taxa percentual proporcionalmente ao valor dos itens; taxa fixa por quantidade. Custos vêm do cálculo salvo. Pedidos antigos sem custos por item precisam ser recalculados pelo botão “Recalcular todos os pedidos”.</p>
      <SummaryTable title="Lucro por canal" groups={report.channels}/>
      <SummaryTable title="Vendas por produto" groups={report.products}/>
      <h3>Detalhamento dos itens vendidos</h3>
      <div className="report-table glass"><table><thead><tr>{["Pedido","Data","Ecommerce","Produto","SKU","Quantidade","Faturamento sem frete","Custos","Taxas","Impostos","Lucro"].map(label=><th key={label} scope="col">{label}</th>)}</tr></thead>
      <tbody>{report.lines.slice((page-1)*25,page*25).map((line,index)=><tr key={`${line.orderId}-${index}`}><td>{line.number}</td><td>{line.date?.split("-").reverse().join("/")}</td><td>{line.channel}</td><td>{line.product}</td><td>{line.sku}</td><td>{quantity(line.quantity)}</td><td>{money(line.revenue)}</td><td>{money(line.cost)}</td><td>{money(line.fees)}</td><td>{money(line.tax)}</td><td>{money(line.profit)}{line.pending&&<small>{line.pending}</small>}</td></tr>)}</tbody></table>{!count&&<p>Nenhuma venda encontrada para os filtros.</p>}</div>
      {count>0&&<nav className="orders-pagination glass" aria-label="Páginas dos itens financeiros"><span>{count} item(ns). Página {page} de {pageCount}.</span><div className="orders-page-controls"><button type="button" disabled={page===1} onClick={()=>setPage(page-1)}>Anterior</button><button type="button" disabled={page===pageCount} onClick={()=>setPage(page+1)}>Próxima</button></div></nav>}
    </>}
  </section>;
}