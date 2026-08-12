param(
    [string]$ApiBase = 'http://localhost:8080',
    [string]$JwtSecret = $env:FINANCIALSYSTEM_JWT_SECRET,
    [string]$JwtSubject = '3',
    [string]$JwtEmail = 'usuario@localhost'
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    throw 'Defina FINANCIALSYSTEM_JWT_SECRET antes de gerar o relatorio.'
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-LocalJwt {
    $header = '{"alg":"HS256","typ":"JWT"}'
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = "{`"sub`":`"$JwtSubject`",`"email`":`"$JwtEmail`",`"iat`":$now,`"exp`":$($now + 86400)}"
    $encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $unsigned = "$encodedHeader.$encodedPayload"
    $hmac = [Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($JwtSecret)
    )
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    return "$unsigned.$signature"
}

function Invoke-WithRateLimit([string]$Uri, [hashtable]$Headers) {
    $attempt = 0
    while ($true) {
        try {
            return Invoke-RestMethod -Uri $Uri -Headers $Headers -TimeoutSec 180
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            if ($status -ne 429 -or ++$attempt -gt 5) { throw }
            Write-Output "Limite 429 recebido. Aguardando 65 segundos antes de continuar..."
            Start-Sleep -Seconds 65
        }
    }
}

$headers = @{ Authorization = "Bearer $(New-LocalJwt)" }
$ordersResponse = Invoke-WithRateLimit "$ApiBase/olist/pedidos?dataInicial=2026-08-08&dataFinal=2026-08-09" $headers
$orders = @($ordersResponse.itens | Where-Object { $_.ecommerce.nome -eq 'Mercado Livre Fulfillment' })
$products = @{}
$processed = 0

foreach ($order in $orders) {
    if ($processed -gt 0) { Start-Sleep -Milliseconds 2600 }
    $detail = Invoke-WithRateLimit "$ApiBase/olist/pedidos/$($order.id)" $headers
    $processed++
    if ($processed % 10 -eq 0 -or $processed -eq $orders.Count) {
        Write-Output "Pedidos processados: $processed/$($orders.Count)"
    }

    foreach ($item in @($detail.itens)) {
        $key = [string]$item.produto.id
        if (!$products.ContainsKey($key)) {
            $products[$key] = [ordered]@{
                Id = $item.produto.id
                Sku = $item.produto.sku
                Description = $item.produto.descricao
                Quantity = 0
                GrossValue = 0.0
                Orders = [Collections.Generic.HashSet[string]]::new()
            }
        }
        $quantity = [int]$item.quantidade
        $products[$key].Quantity += $quantity
        $products[$key].GrossValue += $quantity * [double]$item.valorUnitario
        $orderNumber = if ($null -ne $detail.numeroPedido) { $detail.numeroPedido } else { $detail.id }
        [void]$products[$key].Orders.Add([string]$orderNumber)
    }
}

$rows = @($products.Values | Sort-Object Description)
$cachePath = Join-Path $PSScriptRoot 'relatorio_produtos_vendidos_fulfillment_cache.json'
[IO.File]::WriteAllText($cachePath, ($rows | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
$totalUnits = 0
$totalGross = 0.0
foreach ($row in $rows) {
    $totalUnits += [int]$row.Quantity
    $totalGross += [double]$row.GrossValue
}
$culture = [Globalization.CultureInfo]::GetCultureInfo('pt-BR')
$htmlRows = foreach ($row in $rows) {
    $description = [Net.WebUtility]::HtmlEncode([string]$row.Description)
    $sku = [Net.WebUtility]::HtmlEncode([string]$row.Sku)
    $gross = ([decimal]$row.GrossValue).ToString('C2', $culture)
    "<tr><td>$($row.Id)</td><td>$sku</td><td>$description</td><td class='number'>$($row.Quantity)</td><td class='number'>$($row.Orders.Count)</td><td class='number'>$gross</td></tr>"
}

$generatedAt = (Get-Date).ToString('dd/MM/yyyy HH:mm')
$totalGrossFormatted = ([decimal]$totalGross).ToString('C2', $culture)
$html = @"
<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><style>
@page { size: A4 landscape; margin: 14mm; }
body { font-family: Arial, sans-serif; color: #172033; font-size: 11px; }
h1 { font-size: 22px; margin: 0 0 5px; }
.subtitle { color: #596579; margin-bottom: 18px; }
.summary { display: flex; gap: 12px; margin-bottom: 18px; }
.card { border: 1px solid #dce2ea; border-radius: 7px; padding: 10px 14px; min-width: 140px; }
.card span { display: block; color: #687386; font-size: 10px; }
.card strong { display: block; margin-top: 4px; font-size: 16px; }
table { width: 100%; border-collapse: collapse; }
th { background: #172033; color: white; padding: 8px; text-align: left; }
td { border-bottom: 1px solid #e4e8ee; padding: 7px 8px; }
tr:nth-child(even) td { background: #f7f9fb; }
.number { text-align: right; white-space: nowrap; }
footer { margin-top: 14px; color: #7a8494; font-size: 9px; }
</style></head><body>
<h1>Produtos vendidos</h1>
<div class="subtitle">Mercado Livre Fulfillment — pedidos de 08/08/2026 a 09/08/2026</div>
<div class="summary">
  <div class="card"><span>Pedidos analisados</span><strong>$($orders.Count)</strong></div>
  <div class="card"><span>Produtos distintos</span><strong>$($rows.Count)</strong></div>
  <div class="card"><span>Unidades vendidas</span><strong>$totalUnits</strong></div>
  <div class="card"><span>Valor bruto dos itens</span><strong>$totalGrossFormatted</strong></div>
</div>
<table><thead><tr><th>ID Tiny</th><th>SKU</th><th>Produto</th><th class="number">Unidades</th><th class="number">Pedidos</th><th class="number">Valor bruto</th></tr></thead>
<tbody>$($htmlRows -join "`n")</tbody></table>
<footer>Gerado em $generatedAt. Inclui todos os itens vendidos nos pedidos do canal Mercado Livre Fulfillment, agrupados por produto.</footer>
</body></html>
"@

$outputPath = Join-Path $PSScriptRoot 'relatorio_produtos_simples_mercado_livre_fulfillment_2026-08-08_2026-08-09.html'
[IO.File]::WriteAllText($outputPath, $html, [Text.UTF8Encoding]::new($false))
Write-Output "HTML gerado: $outputPath"
