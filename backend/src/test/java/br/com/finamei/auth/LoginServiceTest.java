package br.com.finamei.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finamei.auth.dto.LoginRequest;
import br.com.finamei.security.JwtService;
import br.com.finamei.shared.error.AccountLockedException;
import br.com.finamei.shared.error.InvalidCredentialsException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	private static final String EMAIL = "maria@exemplo.com";

	@Mock
	private UserRepository userRepository;

	@Mock
	private LoginAttemptRepository loginAttemptRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private MutableClock clock;
	private LoginService loginService;

	@BeforeEach
	void configurar() {
		clock = new MutableClock(Instant.parse("2026-09-15T12:00:00Z"));
		loginService = new LoginService(
				userRepository, loginAttemptRepository, passwordEncoder, jwtService, clock, 5, 15);
	}

	@Test
	void deveAutenticarEGerarTokenComCredenciaisCorretas() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(passwordEncoder.matches("senha123", "hash-armazenado")).thenReturn(true);
		when(jwtService.generateToken(usuario)).thenReturn("token-gerado");
		when(jwtService.getExpirationSeconds()).thenReturn(1800L);

		var resposta = loginService.login(new LoginRequest(" Maria@Exemplo.com ", "senha123"));

		assertThat(resposta.token()).isEqualTo("token-gerado");
		assertThat(resposta.tokenType()).isEqualTo("Bearer");
		assertThat(resposta.expiresInSeconds()).isEqualTo(1800L);
		verify(loginAttemptRepository, never()).save(any());
	}

	@Test
	void deveRejeitarQuandoEmailNaoExisteEContarATentativa() {
		when(userRepository.findByEmail("inexistente@exemplo.com")).thenReturn(Optional.empty());
		when(loginAttemptRepository.findByEmail("inexistente@exemplo.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginRequest("inexistente@exemplo.com", "senha123")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(loginAttemptRepository).save(any(LoginAttempt.class));
	}

	@Test
	void deveRejeitarQuandoSenhaEstaIncorreta() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(passwordEncoder.matches("senhaErrada", "hash-armazenado")).thenReturn(false);

		assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "senhaErrada")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void deveRejeitarQuandoUsuarioEstaInativo() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", false);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "senha123")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void deveBloquearNaQuintaFalhaSeguida() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		LoginAttempt tentativa = new LoginAttempt(EMAIL, clock.instant());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tentativa));
		when(passwordEncoder.matches("errada", "hash-armazenado")).thenReturn(false);

		for (int i = 1; i <= 4; i++) {
			assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "errada")))
					.isInstanceOf(InvalidCredentialsException.class);
		}
		assertThat(tentativa.getFailedAttempts()).isEqualTo(4);
		assertThat(tentativa.getLockedUntil()).isNull();

		assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "errada")))
				.isInstanceOf(AccountLockedException.class)
				.satisfies(ex -> assertThat(((AccountLockedException) ex).getRetryAfterSeconds()).isEqualTo(900));
		assertThat(tentativa.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
	}

	@Test
	void deveRecusarSenhaCorretaDuranteOBloqueioSemConsultarASenha() throws Exception {
		LoginAttempt tentativa = tentativaBloqueada();
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tentativa));
		clock.advance(Duration.ofMinutes(14));

		assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "senha123")))
				.isInstanceOf(AccountLockedException.class)
				.satisfies(ex -> assertThat(((AccountLockedException) ex).getRetryAfterSeconds()).isEqualTo(60));

		verify(userRepository, never()).findByEmail(any());
		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void deveLiberarOLoginQuandoOPeriodoDeBloqueioTermina() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		LoginAttempt tentativa = tentativaBloqueada();
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tentativa));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(passwordEncoder.matches("senha123", "hash-armazenado")).thenReturn(true);
		when(jwtService.generateToken(usuario)).thenReturn("token-gerado");
		clock.advance(Duration.ofMinutes(15));

		var resposta = loginService.login(new LoginRequest(EMAIL, "senha123"));

		assertThat(resposta.token()).isEqualTo("token-gerado");
		verify(loginAttemptRepository).delete(tentativa);
	}

	@Test
	void deveRecomecarAContagemDoZeroDepoisDeUmBloqueioExpirado() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		LoginAttempt tentativa = tentativaBloqueada();
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tentativa));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(passwordEncoder.matches("errada", "hash-armazenado")).thenReturn(false);
		clock.advance(Duration.ofMinutes(16));

		assertThatThrownBy(() -> loginService.login(new LoginRequest(EMAIL, "errada")))
				.isInstanceOf(InvalidCredentialsException.class);

		assertThat(tentativa.getFailedAttempts()).isEqualTo(1);
		assertThat(tentativa.getLockedUntil()).isNull();
	}

	@Test
	void deveZerarAsTentativasQuandoOLoginDaCerto() throws Exception {
		User usuario = criarUsuario(EMAIL, "hash-armazenado", true);
		LoginAttempt tentativa = new LoginAttempt(EMAIL, clock.instant());
		tentativa.registerFailure(clock.instant(), 5, Duration.ofMinutes(15));
		when(loginAttemptRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tentativa));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
		when(passwordEncoder.matches("senha123", "hash-armazenado")).thenReturn(true);
		when(jwtService.generateToken(usuario)).thenReturn("token-gerado");

		loginService.login(new LoginRequest(EMAIL, "senha123"));

		verify(loginAttemptRepository).delete(tentativa);
	}

	private LoginAttempt tentativaBloqueada() {
		LoginAttempt tentativa = new LoginAttempt(EMAIL, clock.instant());
		for (int i = 0; i < 5; i++) {
			tentativa.registerFailure(clock.instant(), 5, Duration.ofMinutes(15));
		}
		return tentativa;
	}

	private User criarUsuario(String email, String senhaHash, boolean ativo) throws Exception {
		User usuario = new User("Maria Silva", email, senhaHash);
		Field campoId = User.class.getDeclaredField("id");
		campoId.setAccessible(true);
		campoId.set(usuario, UUID.randomUUID());

		if (!ativo) {
			Field campoAtivo = User.class.getDeclaredField("active");
			campoAtivo.setAccessible(true);
			campoAtivo.set(usuario, false);
		}

		return usuario;
	}
}
