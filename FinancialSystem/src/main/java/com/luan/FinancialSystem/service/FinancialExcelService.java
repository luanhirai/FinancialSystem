package com.luan.FinancialSystem.service;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Minimal OOXML workbook: numeric money cells, inline text, no executable formulas. */
@Service
public class FinancialExcelService {
    public byte[] export(FinancialReportService.Report report) {
        List<List<Object>> detail=new ArrayList<>();
        detail.add(List.of("Pedido","Data","Ecommerce","ID produto Tiny","Produto","SKU","Quantidade","Faturamento sem frete","Custos","Taxas","Impostos","Lucro","Pendência"));
        for(var l:report.lines()) detail.add(Arrays.asList(l.number(),l.date(),l.channel(),l.productId(),l.product(),l.sku(),l.quantity(),l.revenue(),l.cost(),l.fees(),l.tax(),l.profit(),l.pending()));
        List<List<Object>> summary=new ArrayList<>();
        summary.add(List.of("Período inicial",report.start(),"Período final",report.end()));
        summary.add(List.of("Produto",report.productId(),"Ecommerce",report.channel()));
        summary.add(List.of("Cancelados excluídos no período",report.cancelled()));
        summary.add(List.of("Valores parciais quando existem pendências. Custos históricos ausentes exigem recálculo explícito."));
        summary.add(List.of("Grupo","Nome","Pedidos","Quantidade","Faturamento sem frete","Custos","Taxas","Impostos","Lucro","Pedidos pendentes"));
        summary.add(row("Total","Todos",report.totals()));
        for(var g:report.channels()) summary.add(row("Ecommerce",g.name(),g.totals()));
        for(var g:report.products()) summary.add(row("Produto",g.name(),g.totals()));
        try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            entry(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Resumo\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Itens\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>");
            entry(zip,"xl/worksheets/sheet1.xml",sheet(summary));entry(zip,"xl/worksheets/sheet2.xml",sheet(detail));
            zip.finish();return bytes.toByteArray();
        } catch(IOException e) {throw new IllegalStateException("Não foi possível gerar o Excel.",e);}
    }
    private List<Object> row(String kind,String name,FinancialReportService.Totals t) {return Arrays.asList(kind,name,t.orders(),t.quantity(),t.revenue(),t.cost(),t.fees(),t.tax(),t.profit(),t.pending());}
    private void entry(ZipOutputStream zip,String path,String xml)throws IOException {zip.putNextEntry(new ZipEntry(path));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private String sheet(List<List<Object>> rows) {
        StringBuilder xml=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><sheetData>");
        int index=0;
        for(var row:rows) {
            xml.append("<row r=\"").append(++index).append("\">");
            int column=0;
            for(var value:row) {
                String ref=column(++column)+index;
                if(value==null) {xml.append("<c r=\"").append(ref).append("\"/>");continue;}
                if(value instanceof Number) xml.append("<c r=\"").append(ref).append("\"><v>").append(value).append("</v></c>");
                else xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value.toString())).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }
    private String column(int n) {String s="";while(n>0){n--;s=(char)('A'+n%26)+s;n/=26;}return s;}
    private String escape(String text) {StringBuilder clean=new StringBuilder();text.codePoints().filter(c->c==9||c==10||c==13||(c>=32&&c<=0xd7ff)||(c>=0xe000&&c<=0xfffd)||(c>=0x10000&&c<=0x10ffff)).limit(32767).forEach(clean::appendCodePoint);return clean.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}