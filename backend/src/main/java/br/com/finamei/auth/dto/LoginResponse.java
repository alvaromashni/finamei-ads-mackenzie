package br.com.finamei.auth.dto;

import java.time.OffsetDateTime;

/**
 * @param lastAccessAt acesso anterior a este login; nulo no primeiro acesso
 */
public record LoginResponse(
		String token,
		String tokenType,
		long expiresInSeconds,
		UserResponse user,
		OffsetDateTime lastAccessAt) {

	public static LoginResponse of(String token, long expiresInSeconds, UserResponse user, OffsetDateTime lastAccessAt) {
		return new LoginResponse(token, "Bearer", expiresInSeconds, user, lastAccessAt);
	}
}
