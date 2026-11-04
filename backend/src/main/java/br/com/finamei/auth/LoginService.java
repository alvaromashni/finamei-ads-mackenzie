package br.com.finamei.auth;

import br.com.finamei.auth.dto.LoginRequest;
import br.com.finamei.auth.dto.LoginResponse;
import br.com.finamei.security.JwtService;
import br.com.finamei.shared.error.AccountLockedException;
import br.com.finamei.shared.error.InvalidCredentialsException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticação com bloqueio por tentativas (RN09): na quinta falha seguida do mesmo e-mail a
 * conta fica bloqueada pelo período configurado, mesmo para a senha correta. Login correto
 * zera a contagem. O contador vale para e-mails inexistentes também, então a resposta não
 * revela se o e-mail tem conta.
 */
@Service
public class LoginService {

	private final UserRepository userRepository;
	private final LoginAttemptRepository loginAttemptRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock clock;
	private final int maxFailedAttempts;
	private final Duration lockDuration;

	public LoginService(
			UserRepository userRepository,
			LoginAttemptRepository loginAttemptRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			Clock clock,
			@Value("${finamei.auth.max-failed-attempts:5}") int maxFailedAttempts,
			@Value("${finamei.auth.lock-minutes:15}") long lockMinutes) {
		this.userRepository = userRepository;
		this.loginAttemptRepository = loginAttemptRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.clock = clock;
		this.maxFailedAttempts = maxFailedAttempts;
		this.lockDuration = Duration.ofMinutes(lockMinutes);
	}

	// As falhas precisam ser gravadas mesmo quando a exceção sobe, então não há rollback para elas.
	@Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
	public LoginResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase();
		Instant now = clock.instant();

		LoginAttempt attempt = loginAttemptRepository.findByEmail(email).orElse(null);
		if (attempt != null) {
			if (attempt.isLocked(now)) {
				throw new AccountLockedException(Duration.between(now, attempt.getLockedUntil()));
			}
			attempt.clearExpiredLock(now);
		}

		User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
		boolean authenticated = user != null && passwordEncoder.matches(request.password(), user.getPasswordHash());

		if (!authenticated) {
			if (attempt == null) {
				attempt = new LoginAttempt(email, now);
			}
			attempt.registerFailure(now, maxFailedAttempts, lockDuration);
			loginAttemptRepository.save(attempt);
			if (attempt.isLocked(now)) {
				throw new AccountLockedException(lockDuration);
			}
			throw new InvalidCredentialsException();
		}

		if (attempt != null) {
			loginAttemptRepository.delete(attempt);
		}
		String token = jwtService.generateToken(user);
		return LoginResponse.of(token, jwtService.getExpirationSeconds());
	}
}
