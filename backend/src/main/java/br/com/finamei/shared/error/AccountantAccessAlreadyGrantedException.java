package br.com.finamei.shared.error;

public class AccountantAccessAlreadyGrantedException extends RuntimeException {

	public AccountantAccessAlreadyGrantedException() {
		super("Este contador já tem acesso aos seus dados.");
	}
}
