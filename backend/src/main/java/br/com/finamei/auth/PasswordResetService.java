package br.com.finamei.auth;

import br.com.finamei.shared.error.PasswordResetException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solicitação e confirmação da redefinição de senha (UC-002, RN10). */
@Service
public class PasswordResetService {

	static final String NEUTRAL_MESSAGE =
			"Se este e-mail estiver cadastrado, enviamos um link para redefinir a senha. Ele vale por 30 minutos.";

	static final String SUCCESS_MESSAGE = "Senha redefinida com sucesso. Entre com a nova senha.";

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetMailer mailer;
	private final Clock clock;
	private final Duration ttl;
	private final String publicUrl;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(
			UserRepository userRepository,
			PasswordResetTokenRepository tokenRepository,
			PasswordEncoder passwordEncoder,
			PasswordResetMailer mailer,
			Clock clock,
			@Value("${finamei.auth.password-reset-minutes:30}") long resetMinutes,
			@Value("${app.public-url:http://localhost:5173}") String publicUrl) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailer = mailer;
		this.clock = clock;
		this.ttl = Duration.ofMinutes(resetMinutes);
		this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
	}

	/**
	 * Sempre a mesma resposta, exista ou não o e-mail. Conta inativa também não recebe link:
	 * a reativação é feita pelo administrador.
	 */
	@Transactional
	public String requestReset(String rawEmail) {
		String email = rawEmail.trim().toLowerCase();
		userRepository.findByEmail(email).filter(User::isActive).ifPresent(this::issueToken);
		return NEUTRAL_MESSAGE;
	}

	@Transactional
	public String confirm(String rawToken, String newPassword) {
		PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken.trim()))
				.orElseThrow(PasswordResetException::invalid);
		Instant now = clock.instant();
		if (token.isUsed()) {
			throw PasswordResetException.used();
		}
		if (token.isExpired(now)) {
			throw PasswordResetException.expired();
		}

		User user = userRepository.findById(token.getUserId()).orElseThrow(PasswordResetException::invalid);
		user.changePassword(passwordEncoder.encode(newPassword));
		user.invalidateSessions();
		userRepository.save(user);
		tokenRepository.markUnusedAsUsed(user.getId(), now);
		return SUCCESS_MESSAGE;
	}

	private void issueToken(User user) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = clock.instant();
		tokenRepository.save(new PasswordResetToken(user.getId(), hash(raw), now.plus(ttl), now));
		String link = publicUrl + "/redefinir-senha?token=" + raw;
		mailer.send(user.getEmail(), user.getName(), link);
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível", ex);
		}
	}
}
