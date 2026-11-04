package br.com.finamei.shared.error;

/** O administrador não pode desativar a própria conta (evita ficar sem acesso administrativo). */
public class CannotDeactivateOwnAccountException extends RuntimeException {

	public CannotDeactivateOwnAccountException() {
		super("O administrador não pode desativar a própria conta.");
	}
}
