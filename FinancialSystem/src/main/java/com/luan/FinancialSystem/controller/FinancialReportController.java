package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/olist/relatorios/financeiro")
public class FinancialReportController {
    private final FinancialReportService reports;
    private final FinancialExcelService excel;
    public FinancialReportController(FinancialReportService reports,FinancialExcelService excel) {this.reports=reports;this.excel=excel;}
    @GetMapping
    public FinancialReportService.Report report(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataInicial,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataFinal,
        @RequestParam(required=false) String produtoId,@RequestParam(required=false) String ecommerce) {
        return reports.report(dataInicial,dataFinal,produtoId,ecommerce);
    }
    @GetMapping("/excel")
    public ResponseEntity<byte[]> export(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataInicial,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataFinal,
        @RequestParam(required=false) String produtoId,@RequestParam(required=false) String ecommerce) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=financeiro-"+dataInicial+"-"+dataFinal+".xlsx")
            .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel.export(reports.report(dataInicial,dataFinal,produtoId,ecommerce)));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e) {return ResponseEntity.badRequest().body(e.getMessage());}
}