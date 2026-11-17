package br.com.finamei.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usado quando não há servidor de e-mail configurado. O link fica no log da API
 * para o ambiente local e para o Render sem SMTP.
 */
public class LoggingPasswordResetMailer implements PasswordResetMailer {

	private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

	@Override
	public void send(String email, String name, String link) {
		log.info("Link de redefinição de senha para {}: {}", email, link);
	}
}
