package br.com.finamei.report;

import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.ExportRow;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o relatório em PDF ou planilha (OF16) a partir do mesmo {@link ReportService#generate}, para
 * que os valores conferam com a API de relatório. Tudo local, sem serviço externo.
 *
 * <p>Como em {@link ReportService}, recebe o dono dos dados e deixa a autorização ao chamador.
 */
@Service
public class ReportExportService {

	/** Teto de lançamentos listados na planilha; acima disso a planilha é truncada e avisa. */
	static final int MAX_ROWS = 20_000;

	private static final int PAGE_SIZE = 1_000;

	private final ReportService reportService;
	private final TransactionRepository transactionRepository;
	private final UserRepository userRepository;

	public ReportExportService(
			ReportService reportService, TransactionRepository transactionRepository, UserRepository userRepository) {
		this.reportService = reportService;
		this.transactionRepository = transactionRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public ExportedReport export(
			UUID ownerId, ReportPeriod period, int year, Integer month, ReportExportFormat format) {
		ReportResponse report = reportService.generate(ownerId, period, year, month);
		User owner = userRepository.findById(ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

		byte[] content;
		try {
			if (format == ReportExportFormat.PDF) {
				content = ReportFileWriters.pdf(owner.getName(), report);
			} else {
				Rows rows = loadRows(ownerId, report);
				content = ReportFileWriters.xlsx(owner.getName(), report, rows.items(), rows.truncated());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao gerar o arquivo do relatório.", e);
		}
		return new ExportedReport(content, filename(report, format), format.contentType());
	}

	static String filename(ReportResponse report, ReportExportFormat format) {
		String base = report.period() == ReportPeriod.MONTHLY
				? "relatorio-%d-%02d".formatted(report.year(), report.month())
				: "relatorio-%d".formatted(report.year());
		return base + "." + format.extension();
	}

	private Rows loadRows(UUID ownerId, ReportResponse report) {
		LocalDate start;
		LocalDate end;
		if (report.period() == ReportPeriod.MONTHLY) {
			YearMonth ym = YearMonth.of(report.year(), report.month());
			start = ym.atDay(1);
			end = ym.atEndOfMonth();
		} else {
			start = LocalDate.of(report.year(), 1, 1);
			end = LocalDate.of(report.year(), 12, 31);
		}
		List<ExportRow> items = new ArrayList<>();
		boolean truncated = false;
		int page = 0;
		Slice<ExportRow> slice;
		do {
			slice = transactionRepository.findExportRows(ownerId, start, end, PageRequest.of(page++, PAGE_SIZE));
			for (ExportRow row : slice.getContent()) {
				if (items.size() >= MAX_ROWS) {
					truncated = true;
					break;
				}
				items.add(row);
			}
		} while (slice.hasNext() && !truncated);
		return new Rows(items, truncated);
	}

	private record Rows(List<ExportRow> items, boolean truncated) {
	}
}
