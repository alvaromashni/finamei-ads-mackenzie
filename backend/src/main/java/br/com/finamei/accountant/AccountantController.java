package br.com.finamei.accountant;

import br.com.finamei.accountant.dto.LinkedClientResponse;
import br.com.finamei.report.ReportExportFormat;
import br.com.finamei.report.ReportPeriod;
import br.com.finamei.report.dto.ReportResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Área do contador: somente leitura, somente role ACCOUNTANT (regra em SecurityConfig). O contador é
 * sempre o usuário do token; o cliente só é acessível com vínculo ativo, verificado no service.
 */
@RestController
@RequestMapping("/api/v1/accountant")
public class AccountantController {

	private final AccountantClientService service;

	public AccountantController(AccountantClientService service) {
		this.service = service;
	}

	@GetMapping("/clients")
	public ResponseEntity<List<LinkedClientResponse>> listClients(@AuthenticationPrincipal UUID accountantId) {
		return ResponseEntity.ok(service.listClients(accountantId));
	}

	@GetMapping("/clients/{clientId}/reports")
	public ResponseEntity<ReportResponse> getClientReport(
			@AuthenticationPrincipal UUID accountantId,
			@PathVariable UUID clientId,
			@RequestParam ReportPeriod period,
			@RequestParam int year,
			@RequestParam(required = false) Integer month) {
		return ResponseEntity.ok(service.getClientReport(accountantId, clientId, period, year, month));
	}

	@GetMapping("/clients/{clientId}/reports/export")
	public ResponseEntity<byte[]> exportClientReport(
			@AuthenticationPrincipal UUID accountantId,
			@PathVariable UUID clientId,
			@RequestParam ReportPeriod period,
			@RequestParam int year,
			@RequestParam(required = false) Integer month,
			@RequestParam ReportExportFormat format) {
		return service.exportClientReport(accountantId, clientId, period, year, month, format).toResponse();
	}
}
