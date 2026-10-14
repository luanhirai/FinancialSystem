"use client";

import { useEffect, useRef } from "react";

const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const money = value => value == null ? "Pendente" : currency.format(value);
const dateLabel = value => value ? value.split("-").reverse().join("/") : "Sem data";
function parse(value) {
  if (value && typeof value === "object") return value;
  try { return JSON.parse(value) || {}; } catch { return {}; }
}
function OrderDetails({ order }) {
  const details = parse(order.orderDetails);
  const financial = parse(order.financialDetails);
  const items = Array.isArray(details.itens) ? details.itens : [];
  const amounts = [
    ["Total dos produtos", details.valorTotalProdutos],
    ["Desconto", details.valorDesconto],
    ["Frete", details.valorFrete],
    ["Outras despesas", details.valorOutrasDespesas],
    ["Total do pedido", financial.total ?? details.valorTotalPedido],
    ["Custo dos produtos", financial.custoProdutos],
    ["Taxa percentual do ecommerce", financial.taxaPercentual],
    ["Taxa fixa do ecommerce", financial.taxaFixa],
    ["Imposto sobre a venda", financial.impostoVenda],
    ["Frete excluído do cálculo", financial.freteExcluido],
    ["Valor real", order.realValue],
  ];
  return <>
    <section aria-labelledby="order-items-title">
      <h3 id="order-items-title">Produtos do pedido</h3>
      {items.length ? <div className="orders-table"><table>
        <thead><tr>{["Produto", "SKU", "Quantidade", "Valor unitário", "Subtotal"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{items.map((item, index) => <tr key={index}>
          <td>{item.produto?.descricao || "Produto sem descrição"}{item.infoAdicional && <small>{item.infoAdicional}</small>}</td>
          <td>{item.produto?.sku || "—"}</td>
          <td>{item.quantidade ?? "—"}</td>
          <td>{money(item.valorUnitario)}</td>
          <td>{money(item.quantidade != null && item.valorUnitario != null ? Number(item.quantidade) * Number(item.valorUnitario) : null)}</td>
        </tr>)}</tbody>
      </table></div> : <p>Os produtos deste pedido ainda não estão disponíveis.</p>}
    </section>
    <section aria-labelledby="order-values-title">
      <h3 id="order-values-title">Resumo financeiro</h3>
      <dl className="order-amounts">{amounts.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{money(value)}</dd></div>)}</dl>
      {financial.pendencia && <p className="order-pending" role="status"><strong>Valor real pendente: </strong>{financial.pendencia}</p>}
    </section>
  </>;
}

export default function OrderDetailModal({ order, onClose, children }) {
  const dialogRef = useRef(null);
  useEffect(() => {
    const dialog = dialogRef.current;
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement;
    if (!dialog.open) dialog.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      if (dialog.open) dialog.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);
  return (
    <dialog ref={dialogRef} className="order-detail" aria-labelledby="order-detail-title"
      onCancel={event => { event.preventDefault(); onClose(); }}
      onClick={event => {
        if (event.target !== event.currentTarget) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
      }}>
      <header><h2 id="order-detail-title">Pedido #{order.number}</h2><button type="button" autoFocus onClick={() => onClose()}>Fechar detalhes</button></header>
      <p>Data do pedido: {dateLabel(order.orderDate)} · Cadastro: {order.createdAt ? new Date(order.createdAt).toLocaleString("pt-BR", {timeZone:"America/Sao_Paulo"}) : "Sem informação"}</p>
      <dl className="order-summary">
        <div><dt>Cliente</dt><dd>{order.customerName || "Sem informação"}</dd></div>
        <div><dt>Ecommerce</dt><dd>{order.ecommerceName || "Sem informação"}</dd></div>
        <div><dt>Status</dt><dd>{order.statusDescription || order.statusCode || "Sem informação"}</dd></div>
        <div><dt>ID Tiny</dt><dd>{order.olistOrderId || "Sem informação"}</dd></div>
        <div><dt>Total</dt><dd>{money(parse(order.financialDetails).total ?? parse(order.orderDetails).valorTotalPedido)}</dd></div>
        <div><dt>Valor real</dt><dd>{money(order.realValue)}</dd></div>
      </dl>
      {children}
      <OrderDetails order={order} />
    </dialog>
  );
}
