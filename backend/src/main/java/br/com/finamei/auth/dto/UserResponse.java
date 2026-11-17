package br.com.finamei.auth.dto;

import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String name,
		String email,
		Role role,
		boolean active,
		OffsetDateTime createdAt,
		LocalDate meiOpeningDate,
		OffsetDateTime lastAccessAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.isActive(),
				user.getCreatedAt(),
				user.getMeiOpeningDate(),
				user.getLastAccessAt());
	}
}
