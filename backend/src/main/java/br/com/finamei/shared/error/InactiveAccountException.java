package br.com.finamei.shared.error;

/** Senha correta, mas a conta foi inativada pelo administrador (UC-001, fluxo 4c). */
public class InactiveAccountException extends RuntimeException {

	public InactiveAccountException() {
		super("Esta conta está inativa. Entre em contato com o suporte para reativá-la.");
	}
}
