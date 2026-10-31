import Sidebar from "../../components/page";
import FinancialReports from "../../components/FinancialReports";
import "../../dashboard/dashboard.css";
import "../../orders/orders.css";
import "../reports.css";
export default function Page(){return <div className="dashboard-layout"><Sidebar/><main className="reports-main"><h1>Vendas por produto</h1><FinancialReports mode="products"/></main></div>;}
