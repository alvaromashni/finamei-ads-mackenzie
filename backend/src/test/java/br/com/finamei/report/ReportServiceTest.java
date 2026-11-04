package br.com.finamei.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.revenue.RevenueBand;
import br.com.finamei.revenue.RevenueService;
import br.com.finamei.revenue.RevenueSummaryResponse;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.transaction.PeriodTypeTotals;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private RevenueService revenueService;

	private ReportService service;
	private final UUID ownerId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new ReportService(transactionRepository, revenueService);
	}

	@Test
	void deveConsolidarRelatorioMensalComLimitesInclusivosDoMes() {
		when(transactionRepository.totalsByTypeInPeriod(ownerId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
				.thenReturn(List.of(
						new PeriodTypeTotals(TransactionType.INCOME, new BigDecimal("1500.50"), 3),
						new PeriodTypeTotals(TransactionType.EXPENSE, new BigDecimal("400.25"), 2)));
		RevenueSummaryResponse revenue = revenue(2026);
		when(revenueService.getAnnualSummary(ownerId, 2026)).thenReturn(revenue);

		ReportResponse report = service.generate(ownerId, ReportPeriod.MONTHLY, 2026, 2);

		assertThat(report.period()).isEqualTo(ReportPeriod.MONTHLY);
		assertThat(report.year()).isEqualTo(2026);
		assertThat(report.month()).isEqualTo(2);
		assertThat(report.income()).isEqualByComparingTo("1500.50");
		assertThat(report.expense()).isEqualByComparingTo("400.25");
		assertThat(report.balance()).isEqualByComparingTo("1100.25");
		assertThat(report.transactionCount()).isEqualTo(5);
		assertThat(report.revenue()).isSameAs(revenue);
	}

	@Test
	void deveUsarUltimoDiaDeFevereiroEmAnoBissexto() {
		when(transactionRepository.totalsByTypeInPeriod(ownerId, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))
				.thenReturn(List.of());
		when(revenueService.getAnnualSummary(ownerId, 2024)).thenReturn(revenue(2024));

		service.generate(ownerId, ReportPeriod.MONTHLY, 2024, 2);

		verify(transactionRepository).totalsByTypeInPeriod(ownerId, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
	}

	@Test
	void deveConsolidarRelatorioAnualDe1ºDeJaneiroA31DeDezembro() {
		when(transactionRepository.totalsByTypeInPeriod(ownerId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
				.thenReturn(List.of(new PeriodTypeTotals(TransactionType.INCOME, new BigDecimal("900.00"), 1)));
		when(revenueService.getAnnualSummary(ownerId, 2026)).thenReturn(revenue(2026));

		// O mês é ignorado no relatório anual.
		ReportResponse report = service.generate(ownerId, ReportPeriod.ANNUAL, 2026, 7);

		assertThat(report.month()).isNull();
		assertThat(report.income()).isEqualByComparingTo("900.00");
		assertThat(report.expense()).isEqualByComparingTo("0");
		assertThat(report.balance()).isEqualByComparingTo("900.00");
	}

	@Test
	void deveRetornarRelatorioVazioSemLancamentos() {
		when(transactionRepository.totalsByTypeInPeriod(ownerId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
				.thenReturn(List.of());
		when(revenueService.getAnnualSummary(ownerId, 2026)).thenReturn(revenue(2026));

		ReportResponse report = service.generate(ownerId, ReportPeriod.MONTHLY, 2026, 5);

		assertThat(report.income()).isEqualByComparingTo("0");
		assertThat(report.expense()).isEqualByComparingTo("0");
		assertThat(report.balance()).isEqualByComparingTo("0");
		assertThat(report.transactionCount()).isZero();
	}

	@Test
	void deveTerSaldoNegativoQuandoDespesasSuperamReceitas() {
		when(transactionRepository.totalsByTypeInPeriod(ownerId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
				.thenReturn(List.of(new PeriodTypeTotals(TransactionType.EXPENSE, new BigDecimal("50.00"), 1)));
		when(revenueService.getAnnualSummary(ownerId, 2026)).thenReturn(revenue(2026));

		ReportResponse report = service.generate(ownerId, ReportPeriod.MONTHLY, 2026, 5);

		assertThat(report.balance()).isEqualByComparingTo("-50.00");
	}

	@Test
	void deveRejeitarMesInvalidoOuAusenteNoRelatorioMensal() {
		for (Integer month : new Integer[] {null, 0, 13, -1}) {
			assertThatThrownBy(() -> service.generate(ownerId, ReportPeriod.MONTHLY, 2026, month))
					.isInstanceOfSatisfying(FieldValidationException.class,
							ex -> assertThat(ex.getField()).isEqualTo("month"));
		}
		verifyNoInteractions(transactionRepository, revenueService);
	}

	@Test
	void deveRejeitarAnoForaDoIntervaloSuportado() {
		for (int year : new int[] {1999, 0, -5, 2101}) {
			assertThatThrownBy(() -> service.generate(ownerId, ReportPeriod.ANNUAL, year, null))
					.isInstanceOfSatisfying(FieldValidationException.class,
							ex -> assertThat(ex.getField()).isEqualTo("year"));
		}
		verifyNoInteractions(transactionRepository, revenueService);
	}

	private static RevenueSummaryResponse revenue(int year) {
		return new RevenueSummaryResponse(year, BigDecimal.ZERO, new BigDecimal("81000.00"), BigDecimal.ZERO,
				RevenueBand.NORMAL, false, null);
	}
}
