package br.com.finamei.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPasswordResetRequest(
		@NotBlank(message = "Informe o e-mail.")
		@Email(message = "Informe um e-mail válido.")
		@Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
		String email) {
}
