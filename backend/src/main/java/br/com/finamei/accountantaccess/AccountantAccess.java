package br.com.finamei.accountantaccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acesso de leitura que um MEI concede a um contador (RN11). Está ativo enquanto
 * {@code revokedAt} for nulo; a revogação vale na hora.
 */
@Entity
@Table(name = "accountant_access")
public class AccountantAccess {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "mei_id", nullable = false, updatable = false)
	private UUID meiId;

	@Column(name = "accountant_id", nullable = false, updatable = false)
	private UUID accountantId;

	@Column(name = "granted_at", nullable = false)
	private OffsetDateTime grantedAt;

	@Column(name = "revoked_at")
	private OffsetDateTime revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected AccountantAccess() {
	}

	public AccountantAccess(UUID meiId, UUID accountantId, OffsetDateTime grantedAt) {
		this.meiId = meiId;
		this.accountantId = accountantId;
		this.grantedAt = grantedAt;
	}

	@PrePersist
	private void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	private void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}

	public boolean isActive() {
		return revokedAt == null;
	}

	public void revoke(OffsetDateTime when) {
		this.revokedAt = when;
	}

	/** Concede de novo um acesso que havia sido revogado. */
	public void reactivate(OffsetDateTime when) {
		this.grantedAt = when;
		this.revokedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMeiId() {
		return meiId;
	}

	public UUID getAccountantId() {
		return accountantId;
	}

	public OffsetDateTime getGrantedAt() {
		return grantedAt;
	}

	public OffsetDateTime getRevokedAt() {
		return revokedAt;
	}
}
