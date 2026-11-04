package br.com.finamei.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Registro imutável de uma operação de escrita (ONF10): quem, quando, o quê e em qual recurso.
 * Sem setters e com todas as colunas não atualizáveis; só é criado, nunca alterado.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

	static final int MAX_DETAILS_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10, updatable = false)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 30, updatable = false)
	private AuditResourceType resourceType;

	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;

	@Column(length = MAX_DETAILS_LENGTH, updatable = false)
	private String details;

	protected AuditLog() {
		// exigido pelo JPA
	}

	AuditLog(UUID actorUserId, Instant occurredAt, AuditAction action, AuditResourceType resourceType,
			UUID resourceId, String details) {
		this.actorUserId = actorUserId;
		this.occurredAt = occurredAt;
		this.action = action;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.details = details == null || details.length() <= MAX_DETAILS_LENGTH
				? details
				: details.substring(0, MAX_DETAILS_LENGTH);
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public AuditAction getAction() {
		return action;
	}

	public AuditResourceType getResourceType() {
		return resourceType;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public String getDetails() {
		return details;
	}
}
