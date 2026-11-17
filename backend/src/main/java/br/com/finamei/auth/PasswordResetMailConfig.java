package br.com.finamei.auth;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class PasswordResetMailConfig {

	@Bean
	PasswordResetMailer passwordResetMailer(
			@Value("${finamei.mail.host:}") String host,
			@Value("${finamei.mail.port:587}") int port,
			@Value("${finamei.mail.username:}") String username,
			@Value("${finamei.mail.password:}") String password,
			@Value("${finamei.mail.from:}") String from,
			@Value("${finamei.mail.starttls:true}") boolean starttls) {
		if (host.isBlank()) {
			return new LoggingPasswordResetMailer();
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		if (!username.isBlank()) {
			sender.setUsername(username);
			sender.setPassword(password);
		}
		Properties properties = sender.getJavaMailProperties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.auth", Boolean.toString(!username.isBlank()));
		properties.put("mail.smtp.starttls.enable", Boolean.toString(starttls));
		String fromAddress = from.isBlank() ? username : from;
		return new SmtpPasswordResetMailer(sender, fromAddress);
	}
}
