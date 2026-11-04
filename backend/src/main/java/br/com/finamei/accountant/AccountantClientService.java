package br.com.finamei.accountant;

import br.com.finamei.accountant.dto.LinkedClientResponse;
import br.com.finamei.accountantaccess.AccountantAccess;
import br.com.finamei.accountantaccess.AccountantAccessService;
import br.com.finamei.report.ReportPeriod;
import br.com.finamei.report.ReportService;
import br.com.finamei.report.dto.ReportResponse;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Consultas somente leitura do contador aos dados dos clientes que lhe deram acesso (OF17, RN11). */
@Service
public class AccountantClientService {

	private final AccountantAccessService accessService;
	private final UserRepository userRepository;
	private final ReportService reportService;

	public AccountantClientService(
			AccountantAccessService accessService, UserRepository userRepository, ReportService reportService) {
		this.accessService = accessService;
		this.userRepository = userRepository;
		this.reportService = reportService;
	}

	/** Clientes com acesso ativo, em duas consultas (acessos e usuários), sem N+1. */
	public List<LinkedClientResponse> listClients(UUID accountantId) {
		List<AccountantAccess> grants = accessService.findActiveGrants(accountantId);
		Map<UUID, User> clients = userRepository
				.findAllById(grants.stream().map(AccountantAccess::getMeiId).toList())
				.stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return grants.stream()
				.filter(grant -> clients.containsKey(grant.getMeiId()))
				.map(grant -> {
					User client = clients.get(grant.getMeiId());
					return new LinkedClientResponse(client.getId(), client.getName(), client.getEmail(),
							grant.getGrantedAt());
				})
				.toList();
	}

	/**
	 * Relatório do cliente. O vínculo é verificado no banco a cada chamada, então a revogação vale
	 * na hora. Sem vínculo ativo (inexistente ou revogado) responde 403, sem distinguir os casos.
	 */
	public ReportResponse getClientReport(
			UUID accountantId, UUID clientId, ReportPeriod period, int year, Integer month) {
		if (!accessService.hasActiveAccess(accountantId, clientId)) {
			throw new AccessDeniedException("Sem acesso ativo ao cliente.");
		}
		return reportService.generate(clientId, period, year, month);
	}
}
