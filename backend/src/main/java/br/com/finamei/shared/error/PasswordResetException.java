package br.com.finamei.shared.error;

/** Link de redefinição inválido, expirado ou já utilizado (UC-002). */
public class PasswordResetException extends RuntimeException {

	private final String code;

	public PasswordResetException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static PasswordResetException invalid() {
		return new PasswordResetException(
				"PASSWORD_RESET_INVALID",
				"Este link de redefinição é inválido. Solicite um novo link.");
	}

	public static PasswordResetException expired() {
		return new PasswordResetException(
				"PASSWORD_RESET_EXPIRED",
				"Este link expirou. Solicite um novo link para redefinir a senha.");
	}

	public static PasswordResetException used() {
		return new PasswordResetException(
				"PASSWORD_RESET_USED",
				"Este link já foi utilizado. Solicite um novo link para redefinir a senha.");
	}
}
