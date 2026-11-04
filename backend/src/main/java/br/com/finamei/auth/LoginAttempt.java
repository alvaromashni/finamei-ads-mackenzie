package br.com.finamei.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Contador de falhas de login de um e-mail (RN09). Indexado pelo e-mail, existente ou não,
 * para que o bloqueio não revele quais e-mails têm conta.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 180, unique = true, updatable = false)
	private String email;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LoginAttempt() {
	}

	public LoginAttempt(String email, Instant now) {
		this.email = email;
		this.updatedAt = now;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/** Se o bloqueio já terminou, recomeça a contagem do zero. */
	public void clearExpiredLock(Instant now) {
		if (lockedUntil != null && !now.isBefore(lockedUntil)) {
			lockedUntil = null;
			failedAttempts = 0;
			updatedAt = now;
		}
	}

	/** Registra uma falha e bloqueia ao atingir o limite. */
	public void registerFailure(Instant now, int maxAttempts, Duration lockDuration) {
		failedAttempts++;
		updatedAt = now;
		if (failedAttempts >= maxAttempts) {
			lockedUntil = now.plus(lockDuration);
		}
	}

	public String getEmail() {
		return email;
	}

	public int getFailedAttempts() {
		return failedAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}
}
