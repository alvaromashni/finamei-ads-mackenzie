package br.com.finamei.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a auditoria das escritas (ONF10). Exige uma transação em andamento
 * ({@code MANDATORY}): o registro entra na mesma transação da operação auditada, então se ela
 * falhar e der rollback a auditoria some junto, sem registro órfão, e nenhuma operação bem-sucedida
 * fica sem registro.
 *
 * <p>O {@code details} deve conter só o mínimo para rastreio (valores de negócio, ids). Nunca
 * senha, hash, token ou texto livre do usuário.
 */
@Service
public class AuditService {

	private final AuditLogRepository repository;
	private final Clock clock;

	public AuditService(AuditLogRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID actorUserId, AuditAction action, AuditResourceType resourceType,
			UUID resourceId, String details) {
		repository.save(new AuditLog(actorUserId, clock.instant(), action, resourceType, resourceId, details));
	}
}
