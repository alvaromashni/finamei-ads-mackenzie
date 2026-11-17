package br.com.finamei.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 180, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "mei_opening_date")
	private LocalDate meiOpeningDate;

	/** Momento do último login bem-sucedido. Nulo até o primeiro acesso. */
	@Column(name = "last_access_at")
	private OffsetDateTime lastAccessAt;

	/** Incrementado na troca de senha. Tokens com outra versão deixam de valer (RN10). */
	@Column(name = "session_version", nullable = false)
	private int sessionVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected User() {
	}

	public User(String name, String email, String passwordHash) {
		this(name, email, passwordHash, Role.MEI);
	}

	public User(String name, String email, String passwordHash, Role role) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role == null ? Role.MEI : role;
		this.active = true;
	}

	public void updateProfile(String name, String email) {
		this.name = name;
		this.email = email;
	}

	public void changeMeiOpeningDate(LocalDate meiOpeningDate) {
		this.meiOpeningDate = meiOpeningDate;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public void recordAccess(OffsetDateTime when) {
		this.lastAccessAt = when;
	}

	/** Encerra as sessões já emitidas (RN10). O próximo token sai com a nova versão. */
	public void invalidateSessions() {
		this.sessionVersion++;
	}

	public void changeActive(boolean active) {
		this.active = active;
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

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDate getMeiOpeningDate() {
		return meiOpeningDate;
	}

	public OffsetDateTime getLastAccessAt() {
		return lastAccessAt;
	}

	public int getSessionVersion() {
		return sessionVersion;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
