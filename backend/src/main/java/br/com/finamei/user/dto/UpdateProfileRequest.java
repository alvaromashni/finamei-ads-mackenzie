package br.com.finamei.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Campos de perfil que o próprio usuário pode alterar. Senha e papel ficam de fora de propósito. */
public record UpdateProfileRequest(

		@NotBlank(message = "Informe o nome.")
		@Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
		String name,

		@NotBlank(message = "Informe o e-mail.")
		@Email(message = "Informe um e-mail válido.")
		@Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
		String email) {
}
