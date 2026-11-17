package br.com.finamei.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPasswordResetRequest(
		@NotBlank(message = "Este link de redefinição é inválido. Solicite um novo link.")
		String token,

		@NotBlank(message = "Informe a senha.")
		@Pattern(
				regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
				message = "A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número.")
		String password) {
}
