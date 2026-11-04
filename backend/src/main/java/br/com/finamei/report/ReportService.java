package br.com.finamei.report;

import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.revenue.RevenueService;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.transaction.PeriodTypeTotals;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consolida receitas, despesas, saldo e faturamento de um período (OF15, RN03, RN04).
 *
 * <p>Recebe o dono dos dados como parâmetro, sem ler o contexto de segurança, para ser reutilizado
 * por quem decide a autorização: o próprio MEI (token) ou o contador com acesso ativo (RN11).
 */
@Service
public class ReportService {

	static final int MIN_YEAR = 2000;
	static final int MAX_YEAR = 2100;

	private final TransactionRepository transactionRepository;
	private final RevenueService revenueService;

	public ReportService(TransactionRepository transactionRepository, RevenueService revenueService) {
		this.transactionRepository = transactionRepository;
		this.revenueService = revenueService;
	}

	/**
	 * @param ownerId dono dos lançamentos; a autorização de quem consulta é responsabilidade do chamador
	 * @param month obrigatório (1 a 12) quando o período é mensal; ignorado no anual
	 */
	@Transactional(readOnly = true)
	public ReportResponse generate(UUID ownerId, ReportPeriod period, int year, Integer month) {
		validate(period, year, month);

		LocalDate start;
		LocalDate end;
		Integer reportMonth = null;
		if (period == ReportPeriod.MONTHLY) {
			YearMonth yearMonth = YearMonth.of(year, month);
			start = yearMonth.atDay(1);
			end = yearMonth.atEndOfMonth();
			reportMonth = month;
		} else {
			start = LocalDate.of(year, 1, 1);
			end = LocalDate.of(year, 12, 31);
		}

		Map<TransactionType, PeriodTypeTotals> totals = transactionRepository
				.totalsByTypeInPeriod(ownerId, start, end).stream()
				.collect(Collectors.toMap(PeriodTypeTotals::type, Function.identity()));
		BigDecimal income = totalOf(totals.get(TransactionType.INCOME));
		BigDecimal expense = totalOf(totals.get(TransactionType.EXPENSE));
		long count = totals.values().stream().mapToLong(PeriodTypeTotals::count).sum();

		return new ReportResponse(
				period, year, reportMonth, income, expense, income.subtract(expense), count,
				revenueService.getAnnualSummary(ownerId, year));
	}

	private static BigDecimal totalOf(PeriodTypeTotals totals) {
		return totals == null || totals.total() == null ? BigDecimal.ZERO : totals.total();
	}

	private static void validate(ReportPeriod period, int year, Integer month) {
		if (year < MIN_YEAR || year > MAX_YEAR) {
			throw new FieldValidationException("year", "Informe um ano entre " + MIN_YEAR + " e " + MAX_YEAR + ".");
		}
		if (period == ReportPeriod.MONTHLY && (month == null || month < 1 || month > 12)) {
			throw new FieldValidationException("month", "Informe um mês entre 1 e 12.");
		}
	}
}
