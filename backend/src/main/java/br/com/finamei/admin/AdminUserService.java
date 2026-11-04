package br.com.finamei.admin;

import br.com.finamei.admin.dto.AdminUserResponse;
import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.auth.LoginAttempt;
import br.com.finamei.auth.LoginAttemptRepository;
import br.com.finamei.shared.PageResponse;
import br.com.finamei.shared.error.CannotDeactivateOwnAccountException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão básica de contas pelo administrador: listar, consultar status, ativar/desativar e liberar
 * o bloqueio de login (RN09). Não dá acesso a dados financeiros de ninguém. Toda alteração é
 * auditada (ONF10) com o administrador como autor.
 */
@Service
public class AdminUserService {

	static final int MAX_PAGE_SIZE = 100;
	private static final Sort OLDEST_FIRST = Sort.by("createdAt").and(Sort.by("id"));
	private static final String USER_NOT_FOUND = "Usuário não encontrado.";

	private final UserRepository userRepository;
	private final LoginAttemptRepository loginAttemptRepository;
	private final AuditService auditService;
	private final Clock clock;

	public AdminUserService(UserRepository userRepository, LoginAttemptRepository loginAttemptRepository,
			AuditService auditService, Clock clock) {
		this.userRepository = userRepository;
		this.loginAttemptRepository = loginAttemptRepository;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminUserResponse> list(int page, int size) {
		Page<User> users = userRepository
				.findAll(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), OLDEST_FIRST));
		Instant now = clock.instant();
		Map<String, LoginAttempt> attempts = loginAttemptRepository
				.findByEmailIn(users.getContent().stream().map(User::getEmail).toList()).stream()
				.collect(Collectors.toMap(LoginAttempt::getEmail, Function.identity()));
		return PageResponse.from(users, user -> toResponse(user, attempts.get(user.getEmail()), now));
	}

	@Transactional(readOnly = true)
	public AdminUserResponse get(UUID userId) {
		User user = find(userId);
		return toResponse(user, loginAttemptRepository.findByEmail(user.getEmail()).orElse(null), clock.instant());
	}

	/** Ativa ou desativa a conta. Pedir o status que já vale não altera nada nem gera auditoria. */
	@Transactional
	public AdminUserResponse updateStatus(UUID adminId, UUID userId, boolean active) {
		if (!active && adminId.equals(userId)) {
			throw new CannotDeactivateOwnAccountException();
		}
		User user = find(userId);
		if (user.isActive() != active) {
			user.changeActive(active);
			userRepository.saveAndFlush(user);
			auditService.record(adminId, AuditAction.UPDATE, AuditResourceType.USER, user.getId(),
					active ? "activated" : "deactivated");
		}
		return get(userId);
	}

	/** Remove o bloqueio de login por tentativas (RN09) e zera a contagem. Sem registro de falhas, não faz nada. */
	@Transactional
	public AdminUserResponse unlockLogin(UUID adminId, UUID userId) {
		User user = find(userId);
		loginAttemptRepository.findByEmail(user.getEmail()).ifPresent(attempt -> {
			loginAttemptRepository.delete(attempt);
			loginAttemptRepository.flush();
			auditService.record(adminId, AuditAction.UPDATE, AuditResourceType.USER, user.getId(),
					"login lock cleared");
		});
		return get(userId);
	}

	private User find(UUID userId) {
		return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
	}

	private AdminUserResponse toResponse(User user, LoginAttempt attempt, Instant now) {
		boolean locked = attempt != null && attempt.isLocked(now);
		return AdminUserResponse.from(user, locked, locked ? attempt.getLockedUntil() : null);
	}
}
