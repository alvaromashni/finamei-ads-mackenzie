package br.com.finamei.admin.dto;

import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conta vista pelo administrador. Nunca inclui hash de senha nem dados financeiros.
 *
 * @param loginLocked bloqueio de login (RN09) em vigor neste instante
 * @param loginLockedUntil fim do bloqueio, quando houver
 */
public record AdminUserResponse(
		UUID id,
		String name,
		String email,
		Role role,
		boolean active,
		OffsetDateTime createdAt,
		boolean loginLocked,
		Instant loginLockedUntil) {

	public static AdminUserResponse from(User user, boolean loginLocked, Instant loginLockedUntil) {
		return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
				user.isActive(), user.getCreatedAt(), loginLocked, loginLockedUntil);
	}
}
