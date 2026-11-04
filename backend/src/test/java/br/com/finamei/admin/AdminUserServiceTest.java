package br.com.finamei.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finamei.admin.dto.AdminUserResponse;
import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.auth.LoginAttempt;
import br.com.finamei.auth.LoginAttemptRepository;
import br.com.finamei.shared.error.CannotDeactivateOwnAccountException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

	private static final Instant AGORA = Instant.parse("2026-09-15T12:00:00Z");

	@Mock
	private UserRepository userRepository;

	@Mock
	private LoginAttemptRepository loginAttemptRepository;

	@Mock
	private AuditService auditService;

	private AdminUserService service;
	private final UUID adminId = UUID.randomUUID();

	@BeforeEach
	void criarServico() {
		service = new AdminUserService(userRepository, loginAttemptRepository, auditService,
				Clock.fixed(AGORA, ZoneOffset.UTC));
	}

	@Test
	void deveDesativarContaEAuditarComOAdministradorComoAutor() {
		User maria = usuario("maria@exemplo.com", true);
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));

		AdminUserResponse resposta = service.updateStatus(adminId, maria.getId(), false);

		assertThat(resposta.active()).isFalse();
		assertThat(maria.isActive()).isFalse();
		verify(userRepository).saveAndFlush(maria);
		verify(auditService).record(adminId, AuditAction.UPDATE, AuditResourceType.USER, maria.getId(), "deactivated");
	}

	@Test
	void deveReativarContaEAuditar() {
		User maria = usuario("maria@exemplo.com", false);
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));

		service.updateStatus(adminId, maria.getId(), true);

		assertThat(maria.isActive()).isTrue();
		verify(auditService).record(adminId, AuditAction.UPDATE, AuditResourceType.USER, maria.getId(), "activated");
	}

	@Test
	void naoDeveAlterarNemAuditarQuandoOStatusJaEOPedido() {
		User maria = usuario("maria@exemplo.com", true);
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));

		service.updateStatus(adminId, maria.getId(), true);

		verify(userRepository, never()).saveAndFlush(any());
		verifyNoInteractions(auditService);
	}

	@Test
	void naoDevePermitirQueOAdministradorDesativeASiMesmo() {
		assertThatThrownBy(() -> service.updateStatus(adminId, adminId, false))
				.isInstanceOf(CannotDeactivateOwnAccountException.class);

		verifyNoInteractions(auditService);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void deveResponderNaoEncontradoParaUsuarioInexistente() {
		UUID inexistente = UUID.randomUUID();
		when(userRepository.findById(inexistente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateStatus(adminId, inexistente, false))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.get(inexistente)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.unlockLogin(adminId, inexistente))
				.isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(auditService);
	}

	@Test
	void deveLiberarBloqueioDeLoginEAuditar() {
		User maria = usuario("maria@exemplo.com", true);
		LoginAttempt tentativa = bloqueada("maria@exemplo.com");
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(loginAttemptRepository.findByEmail("maria@exemplo.com"))
				.thenReturn(Optional.of(tentativa), Optional.empty());

		AdminUserResponse resposta = service.unlockLogin(adminId, maria.getId());

		verify(loginAttemptRepository).delete(tentativa);
		verify(auditService).record(eq(adminId), eq(AuditAction.UPDATE), eq(AuditResourceType.USER),
				eq(maria.getId()), eq("login lock cleared"));
		assertThat(resposta.loginLocked()).isFalse();
	}

	@Test
	void liberarSemBloqueioNaoFazNadaENaoAudita() {
		User maria = usuario("maria@exemplo.com", true);
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(loginAttemptRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.empty());

		service.unlockLogin(adminId, maria.getId());

		verify(loginAttemptRepository, never()).delete(any());
		verifyNoInteractions(auditService);
	}

	@Test
	void consultaDeveInformarBloqueioVigenteEIgnorarBloqueioExpirado() {
		User maria = usuario("maria@exemplo.com", true);
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		LoginAttempt vigente = bloqueada("maria@exemplo.com");
		when(loginAttemptRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(vigente));

		AdminUserResponse bloqueada = service.get(maria.getId());

		assertThat(bloqueada.loginLocked()).isTrue();
		assertThat(bloqueada.loginLockedUntil()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));

		LoginAttempt expirada = new LoginAttempt("maria@exemplo.com", AGORA.minus(Duration.ofHours(1)));
		for (int i = 0; i < 5; i++) {
			expirada.registerFailure(AGORA.minus(Duration.ofHours(1)), 5, Duration.ofMinutes(15));
		}
		when(loginAttemptRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(expirada));

		AdminUserResponse livre = service.get(maria.getId());

		assertThat(livre.loginLocked()).isFalse();
		assertThat(livre.loginLockedUntil()).isNull();
	}

	private LoginAttempt bloqueada(String email) {
		LoginAttempt tentativa = new LoginAttempt(email, AGORA);
		for (int i = 0; i < 5; i++) {
			tentativa.registerFailure(AGORA, 5, Duration.ofMinutes(15));
		}
		return tentativa;
	}

	private User usuario(String email, boolean ativo) {
		User usuario = new User("Maria", email, "hash");
		ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
		usuario.changeActive(ativo);
		return usuario;
	}
}
