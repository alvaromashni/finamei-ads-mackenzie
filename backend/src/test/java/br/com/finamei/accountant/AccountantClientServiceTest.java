package br.com.finamei.accountant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finamei.accountantaccess.AccountantAccessService;
import br.com.finamei.report.ReportPeriod;
import br.com.finamei.report.ReportService;
import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AccountantClientServiceTest {

	@Mock
	private AccountantAccessService accessService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ReportService reportService;

	private AccountantClientService service;
	private final UUID accountantId = UUID.randomUUID();
	private final UUID clientId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new AccountantClientService(accessService, userRepository, reportService);
	}

	@Test
	void deveGerarRelatorioDoClienteQuandoHaVinculoAtivo() {
		ReportResponse esperado = new ReportResponse(
				ReportPeriod.ANNUAL, 2026, null, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"), 2, null);
		when(accessService.hasActiveAccess(accountantId, clientId)).thenReturn(true);
		when(reportService.generate(clientId, ReportPeriod.ANNUAL, 2026, null)).thenReturn(esperado);

		assertThat(service.getClientReport(accountantId, clientId, ReportPeriod.ANNUAL, 2026, null))
				.isSameAs(esperado);
	}

	@Test
	void deveNegarSemVinculoAtivoENuncaConsultarOsDados() {
		when(accessService.hasActiveAccess(accountantId, clientId)).thenReturn(false);

		assertThatThrownBy(() -> service.getClientReport(accountantId, clientId, ReportPeriod.ANNUAL, 2026, null))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(reportService);
	}

	@Test
	void deveListarListaVaziaQuandoNaoHaVinculos() {
		when(accessService.findActiveGrants(accountantId)).thenReturn(List.of());
		when(userRepository.findAllById(List.of())).thenReturn(List.of());

		assertThat(service.listClients(accountantId)).isEmpty();
		verify(accessService).findActiveGrants(accountantId);
	}
}
