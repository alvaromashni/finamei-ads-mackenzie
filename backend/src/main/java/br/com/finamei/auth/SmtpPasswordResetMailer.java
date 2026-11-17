package br.com.finamei.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envia o link de redefinição por SMTP (variáveis MAIL_*). */
public class SmtpPasswordResetMailer implements PasswordResetMailer {

	private final JavaMailSender sender;
	private final String from;

	public SmtpPasswordResetMailer(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void send(String email, String name, String link) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("Redefinição de senha do FinaMEI");
		message.setText("""
				Olá, %s

				Recebemos um pedido para redefinir a senha da sua conta no FinaMEI.
				Abra o link abaixo nos próximos 30 minutos:

				%s

				Se você não pediu a redefinição, ignore esta mensagem.
				""".formatted(name, link));
		sender.send(message);
	}
}
