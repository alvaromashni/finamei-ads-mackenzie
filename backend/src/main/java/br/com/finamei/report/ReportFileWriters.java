package br.com.finamei.report;

import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.transaction.ExportRow;
import br.com.finamei.transaction.TransactionType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/** Montagem dos arquivos PDF (OpenPDF) e XLSX (Apache POI) do relatório; sem estado. */
final class ReportFileWriters {

	static final Locale PT_BR = Locale.of("pt", "BR");
	static final String SUMMARY_SHEET = "Resumo";
	static final String TRANSACTIONS_SHEET = "Lançamentos";

	private ReportFileWriters() {
	}

	static String money(BigDecimal value) {
		return NumberFormat.getCurrencyInstance(PT_BR).format(value);
	}

	static String periodLabel(ReportResponse report) {
		if (report.period() == ReportPeriod.MONTHLY) {
			String month = Month.of(report.month()).getDisplayName(TextStyle.FULL, PT_BR);
			return Character.toUpperCase(month.charAt(0)) + month.substring(1) + "/" + report.year();
		}
		return "Ano " + report.year();
	}

	static byte[] pdf(String ownerName, ReportResponse report) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 50, 50, 50, 50);
		try {
			PdfWriter.getInstance(document, out);
			document.open();
			document.addTitle("Relatório FinaMEI - " + periodLabel(report));
			document.add(new Paragraph("Relatório FinaMEI",
					FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
			document.add(new Paragraph("MEI: " + ownerName, FontFactory.getFont(FontFactory.HELVETICA, 12)));
			document.add(new Paragraph("Período: " + periodLabel(report), FontFactory.getFont(FontFactory.HELVETICA, 12)));
			document.add(new Paragraph(" "));

			PdfPTable table = new PdfPTable(new float[] {3, 2});
			table.setWidthPercentage(100);
			addRow(table, "Receitas", money(report.income()), false);
			addRow(table, "Despesas", money(report.expense()), false);
			addRow(table, "Saldo", money(report.balance()), true);
			addRow(table, "Quantidade de lançamentos", String.valueOf(report.transactionCount()), false);
			addRow(table, "Faturamento acumulado em " + report.year(), money(report.revenue().accumulated()), false);
			addRow(table, "Limite anual do MEI", money(report.revenue().limit()), false);
			addRow(table, "Percentual do limite", report.revenue().percentage().toPlainString() + "%", false);
			document.add(table);

			if (report.transactionCount() == 0) {
				document.add(new Paragraph(" "));
				document.add(new Paragraph("Nenhum lançamento no período.",
						FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11)));
			}
			document.add(new Paragraph(" "));
			document.add(new Paragraph("Gerado em " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
					FontFactory.getFont(FontFactory.HELVETICA, 9)));
		} catch (DocumentException e) {
			throw new IOException("Falha ao montar o PDF.", e);
		} finally {
			if (document.isOpen()) {
				document.close();
			}
		}
		return out.toByteArray();
	}

	private static void addRow(PdfPTable table, String label, String value, boolean bold) {
		var font = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 11);
		PdfPCell left = new PdfPCell(new Phrase(label, font));
		PdfPCell right = new PdfPCell(new Phrase(value, font));
		right.setHorizontalAlignment(Element.ALIGN_RIGHT);
		left.setPadding(6);
		right.setPadding(6);
		table.addCell(left);
		table.addCell(right);
	}

	static byte[] xlsx(String ownerName, ReportResponse report, List<ExportRow> rows, boolean truncated)
			throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CreationHelper helper = workbook.getCreationHelper();
			Font bold = workbook.createFont();
			bold.setBold(true);
			CellStyle header = workbook.createCellStyle();
			header.setFont(bold);
			CellStyle money = workbook.createCellStyle();
			money.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
			CellStyle date = workbook.createCellStyle();
			date.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));

			Sheet summary = workbook.createSheet(SUMMARY_SHEET);
			int r = 0;
			r = text(summary, r, header, "Relatório FinaMEI", null);
			r = text(summary, r, null, "MEI", ownerName);
			r = text(summary, r, null, "Período", periodLabel(report));
			r++;
			Row head = summary.createRow(r++);
			head.createCell(0).setCellValue("Consolidado");
			head.createCell(1).setCellValue("Valor");
			head.getCell(0).setCellStyle(header);
			head.getCell(1).setCellStyle(header);
			r = number(summary, r, "Receitas", report.income(), money);
			r = number(summary, r, "Despesas", report.expense(), money);
			r = number(summary, r, "Saldo", report.balance(), money);
			r = number(summary, r, "Quantidade de lançamentos", BigDecimal.valueOf(report.transactionCount()), null);
			r = number(summary, r, "Faturamento acumulado em " + report.year(), report.revenue().accumulated(), money);
			r = number(summary, r, "Limite anual do MEI", report.revenue().limit(), money);
			r = number(summary, r, "Percentual do limite (%)", report.revenue().percentage(), money);
			if (truncated) {
				r++;
				text(summary, r, null, "Aviso",
						"A lista de lançamentos foi limitada a " + ReportExportService.MAX_ROWS
								+ " linhas; os consolidados acima incluem todos os lançamentos.");
			}
			summary.setColumnWidth(0, 9000);
			summary.setColumnWidth(1, 6000);

			Sheet sheet = workbook.createSheet(TRANSACTIONS_SHEET);
			Row titles = sheet.createRow(0);
			String[] columns = {"Data", "Tipo", "Categoria", "Descrição", "Valor"};
			for (int i = 0; i < columns.length; i++) {
				titles.createCell(i).setCellValue(columns[i]);
				titles.getCell(i).setCellStyle(header);
			}
			int line = 1;
			for (ExportRow item : rows) {
				Row row = sheet.createRow(line++);
				row.createCell(0).setCellValue(item.date());
				row.getCell(0).setCellStyle(date);
				row.createCell(1).setCellValue(item.type() == TransactionType.INCOME ? "Receita" : "Despesa");
				row.createCell(2).setCellValue(item.category() == null ? "" : item.category());
				row.createCell(3).setCellValue(item.description());
				row.createCell(4).setCellValue(item.amount().doubleValue());
				row.getCell(4).setCellStyle(money);
			}
			sheet.setColumnWidth(0, 3500);
			sheet.setColumnWidth(1, 3000);
			sheet.setColumnWidth(2, 6000);
			sheet.setColumnWidth(3, 12000);
			sheet.setColumnWidth(4, 4000);
			sheet.createFreezePane(0, 1);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static int text(Sheet sheet, int r, CellStyle style, String label, String value) {
		Row row = sheet.createRow(r);
		row.createCell(0).setCellValue(label);
		if (style != null) {
			row.getCell(0).setCellStyle(style);
		}
		if (value != null) {
			row.createCell(1).setCellValue(value);
		}
		return r + 1;
	}

	private static int number(Sheet sheet, int r, String label, BigDecimal value, CellStyle style) {
		Row row = sheet.createRow(r);
		row.createCell(0).setCellValue(label);
		row.createCell(1).setCellValue(value.doubleValue());
		if (style != null) {
			row.getCell(1).setCellStyle(style);
		}
		return r + 1;
	}
}
