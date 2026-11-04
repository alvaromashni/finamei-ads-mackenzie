package br.com.finamei.shared.error;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

	private final long retryAfterSeconds;

	public AccountLockedException(Duration remaining) {
		super("Muitas tentativas de login malsucedidas. Tente novamente mais tarde.");
		// Arredonda para cima: nunca promete liberação antes da hora.
		this.retryAfterSeconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
