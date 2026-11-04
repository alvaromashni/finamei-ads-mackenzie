package br.com.finamei.report;

import br.com.finamei.report.dto.ReportResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Relatório do usuário autenticado; o dono dos dados vem sempre do token (ONF05). */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping
	public ResponseEntity<ReportResponse> get(
			@AuthenticationPrincipal UUID userId,
			@RequestParam ReportPeriod period,
			@RequestParam int year,
			@RequestParam(required = false) Integer month) {
		return ResponseEntity.ok(reportService.generate(userId, period, year, month));
	}
}
