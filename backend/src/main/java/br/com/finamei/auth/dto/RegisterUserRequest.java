package br.com.finamei.auth.dto;

import br.com.finamei.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

		@NotBlank(message = "Informe o nome.")
		@Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
		String name,

		@NotBlank(message = "Informe o e-mail.")
		@Email(message = "Informe um e-mail válido.")
		@Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
		String email,

		@NotBlank(message = "Informe a senha.")
		@Pattern(
				regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
				message = "A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número.")
		String password,

		/** Opcional. Ausente ou nulo cria um MEI. ADMIN não é aceito por este endpoint. */
		Role role) {
}
