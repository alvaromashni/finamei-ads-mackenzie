package br.com.finamei.accountantaccess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrantAccountantAccessRequest(

		@NotBlank(message = "Informe o e-mail do contador.")
		@Email(message = "Informe um e-mail válido.")
		@Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
		String accountantEmail) {
}
