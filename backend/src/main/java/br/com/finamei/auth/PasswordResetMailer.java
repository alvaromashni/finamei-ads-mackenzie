package br.com.finamei.auth;

/** Envio do link de redefinição de senha (RN10). A implementação depende da configuração. */
public interface PasswordResetMailer {

	void send(String email, String name, String link);
}
