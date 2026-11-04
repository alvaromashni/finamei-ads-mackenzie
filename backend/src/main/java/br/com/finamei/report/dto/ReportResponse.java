package br.com.finamei.report.dto;

import br.com.finamei.report.ReportPeriod;
import br.com.finamei.revenue.RevenueSummaryResponse;
import java.math.BigDecimal;

/**
 * Relatório consolidado de um período.
 *
 * @param month 1 a 12 no relatório mensal; {@code null} no anual
 * @param balance receitas menos despesas do período
 * @param transactionCount lançamentos ativos no período; 0 indica relatório vazio
 * @param revenue faturamento acumulado do ano do relatório contra o limite do MEI (RN01 a RN04)
 */
public record ReportResponse(
		ReportPeriod period,
		int year,
		Integer month,
		BigDecimal income,
		BigDecimal expense,
		BigDecimal balance,
		long transactionCount,
		RevenueSummaryResponse revenue) {
}
