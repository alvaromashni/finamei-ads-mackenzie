package br.com.finamei.accountantaccess;

import br.com.finamei.accountantaccess.dto.AccountantAccessResponse;
import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.shared.error.AccountantAccessAlreadyGrantedException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso de leitura de contadores aos dados de um MEI (RN11). Quem concede e revoga é sempre
 * o MEI dono dos dados; o acesso nunca dá permissão de escrita (ONF05).
 */
@Service
public class AccountantAccessService {

	private static final String ACCOUNTANT_NOT_FOUND = "Não encontramos um contador com esse e-mail.";

	private final AccountantAccessRepository repository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final AuditService auditService;

	public AccountantAccessService(
			AccountantAccessRepository repository, UserRepository userRepository, Clock clock,
			AuditService auditService) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.clock = clock;
		this.auditService = auditService;
	}

	/** Acessos ativos concedidos pelo MEI. */
	@Transactional(readOnly = true)
	public List<AccountantAccessResponse> listActive(UUID meiId) {
		List<AccountantAccess> accesses = repository.findByMeiIdAndRevokedAtIsNullOrderByGrantedAtAsc(meiId);
		Map<UUID, User> accountants = userRepository
				.findAllById(accesses.stream().map(AccountantAccess::getAccountantId).toList())
				.stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		return accesses.stream()
				.map(access -> AccountantAccessResponse.from(access, accountants.get(access.getAccountantId())))
				.toList();
	}

	@Transactional
	public AccountantAccessResponse grant(UUID meiId, String accountantEmail) {
		String email = accountantEmail.trim().toLowerCase();
		// Mesma resposta para e-mail inexistente e para conta que não é de contador:
		// assim a API não revela quais e-mails existem como outros tipos de conta.
		User accountant = userRepository.findByEmail(email)
				.filter(user -> user.getRole() == Role.ACCOUNTANT && user.isActive())
				.orElseThrow(() -> new ResourceNotFoundException(ACCOUNTANT_NOT_FOUND));

		OffsetDateTime now = OffsetDateTime.now(clock);
		boolean[] reactivated = {false};
		AccountantAccess access = repository.findByMeiIdAndAccountantId(meiId, accountant.getId())
				.map(existing -> {
					if (existing.isActive()) {
						throw new AccountantAccessAlreadyGrantedException();
					}
					existing.reactivate(now);
					reactivated[0] = true;
					return existing;
				})
				.orElseGet(() -> new AccountantAccess(meiId, accountant.getId(), now));

		try {
			AccountantAccess saved = repository.saveAndFlush(access);
			auditService.record(meiId, reactivated[0] ? AuditAction.UPDATE : AuditAction.CREATE,
					AuditResourceType.ACCOUNTANT_ACCESS, saved.getId(), "granted accountantId=" + accountant.getId());
			return AccountantAccessResponse.from(saved, accountant);
		} catch (DataIntegrityViolationException ex) {
			// Duas concessões simultâneas: a restrição única do banco decide.
			throw new AccountantAccessAlreadyGrantedException();
		}
	}

	/** Revoga na hora. Acesso inexistente, de outro MEI ou já revogado responde 404. */
	@Transactional
	public void revoke(UUID meiId, UUID accessId) {
		AccountantAccess access = repository.findByIdAndMeiIdAndRevokedAtIsNull(accessId, meiId)
				.orElseThrow(() -> new ResourceNotFoundException("Acesso não encontrado."));
		access.revoke(OffsetDateTime.now(clock));
		repository.saveAndFlush(access);
		auditService.record(meiId, AuditAction.DELETE, AuditResourceType.ACCOUNTANT_ACCESS, access.getId(),
				"revoked accountantId=" + access.getAccountantId());
	}

	/**
	 * Ponto de extensão para as consultas de contador (#33): o contador só pode ler dados do
	 * cliente se houver acesso ativo. Consulta o banco a cada chamada, então a revogação vale
	 * imediatamente. Quem chama deve responder 404 quando for falso.
	 */
	@Transactional(readOnly = true)
	public boolean hasActiveAccess(UUID accountantId, UUID meiId) {
		return repository.existsByMeiIdAndAccountantIdAndRevokedAtIsNull(meiId, accountantId);
	}

	/** Acessos ativos do contador (cliente e data da concessão), do mais antigo ao mais recente. */
	@Transactional(readOnly = true)
	public List<AccountantAccess> findActiveGrants(UUID accountantId) {
		return repository.findByAccountantIdAndRevokedAtIsNullOrderByGrantedAtAsc(accountantId);
	}

	/** Ids dos MEIs que concederam acesso ativo ao contador (base da lista de clientes, #33). */
	@Transactional(readOnly = true)
	public List<UUID> findActiveClientIds(UUID accountantId) {
		return repository.findByAccountantIdAndRevokedAtIsNull(accountantId).stream()
				.map(AccountantAccess::getMeiId)
				.toList();
	}
}
