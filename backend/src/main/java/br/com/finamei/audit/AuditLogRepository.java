package br.com.finamei.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Propositalmente não estende JpaRepository: só expõe inserção e consulta, então nenhum código
 * da aplicação consegue atualizar ou apagar registros de auditoria (ONF10).
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

	AuditLog save(AuditLog auditLog);

	List<AuditLog> findByActorUserIdOrderByOccurredAtAsc(UUID actorUserId);

	List<AuditLog> findByResourceTypeAndResourceId(AuditResourceType resourceType, UUID resourceId);

	long countByActorUserId(UUID actorUserId);
}
