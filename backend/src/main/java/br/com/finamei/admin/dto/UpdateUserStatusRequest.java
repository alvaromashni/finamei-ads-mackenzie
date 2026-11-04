package br.com.finamei.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
		@NotNull(message = "Informe se a conta deve ficar ativa.")
		Boolean active) {
}
