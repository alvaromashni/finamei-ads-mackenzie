package br.com.finamei.accountantaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finamei.accountantaccess.dto.AccountantAccessResponse;
import br.com.finamei.shared.error.AccountantAccessAlreadyGrantedException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountantAccessServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);

	@Mock
	private AccountantAccessRepository repository;

	@Mock
	private UserRepository userRepository;

	private AccountantAccessService service;

	private final UUID meiId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new AccountantAccessService(repository, userRepository, CLOCK);
	}

	@Test
	void deveConcederAcessoANovoContadorComEmailNormalizado() {
		User ana = usuario("Ana", "ana@contabil.com", Role.ACCOUNTANT);
		when(userRepository.findByEmail("ana@contabil.com")).thenReturn(Optional.of(ana));
		when(repository.findByMeiIdAndAccountantId(meiId, ana.getId())).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any(AccountantAccess.class))).thenAnswer(c -> c.getArgument(0));

		AccountantAccessResponse resposta = service.grant(meiId, " Ana@Contabil.com ");

		assertThat(resposta.accountant().name()).isEqualTo("Ana");
		assertThat(resposta.accountant().email()).isEqualTo("ana@contabil.com");
		assertThat(resposta.grantedAt().toInstant()).isEqualTo(CLOCK.instant());
	}

	@Test
	void deveReativarAcessoJaRevogadoEmVezDeDuplicar() {
		User ana = usuario("Ana", "ana@contabil.com", Role.ACCOUNTANT);
		AccountantAccess revogado = new AccountantAccess(meiId, ana.getId(), CLOCK.instant().atOffset(ZoneOffset.UTC).minusDays(10));
		revogado.revoke(CLOCK.instant().atOffset(ZoneOffset.UTC).minusDays(5));
		when(userRepository.findByEmail("ana@contabil.com")).thenReturn(Optional.of(ana));
		when(repository.findByMeiIdAndAccountantId(meiId, ana.getId())).thenReturn(Optional.of(revogado));
		when(repository.saveAndFlush(revogado)).thenReturn(revogado);

		service.grant(meiId, "ana@contabil.com");

		assertThat(revogado.isActive()).isTrue();
		assertThat(revogado.getGrantedAt().toInstant()).isEqualTo(CLOCK.instant());
	}

	@Test
	void deveRecusarConcessaoDuplicadaAtiva() {
		User ana = usuario("Ana", "ana@contabil.com", Role.ACCOUNTANT);
		AccountantAccess ativo = new AccountantAccess(meiId, ana.getId(), CLOCK.instant().atOffset(ZoneOffset.UTC));
		when(userRepository.findByEmail("ana@contabil.com")).thenReturn(Optional.of(ana));
		when(repository.findByMeiIdAndAccountantId(meiId, ana.getId())).thenReturn(Optional.of(ativo));

		assertThatThrownBy(() -> service.grant(meiId, "ana@contabil.com"))
				.isInstanceOf(AccountantAccessAlreadyGrantedException.class);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void deveTraduzirViolacaoDeUnicidadeEmConflito() {
		User ana = usuario("Ana", "ana@contabil.com", Role.ACCOUNTANT);
		when(userRepository.findByEmail("ana@contabil.com")).thenReturn(Optional.of(ana));
		when(repository.findByMeiIdAndAccountantId(meiId, ana.getId())).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any(AccountantAccess.class))).thenThrow(new DataIntegrityViolationException("uk"));

		assertThatThrownBy(() -> service.grant(meiId, "ana@contabil.com"))
				.isInstanceOf(AccountantAccessAlreadyGrantedException.class);
	}

	@Test
	void deveRecusarEmailInexistente() {
		when(userRepository.findByEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.grant(meiId, "ninguem@exemplo.com"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deveRecusarContaQueNaoEContador() {
		User outroMei = usuario("João", "joao@exemplo.com", Role.MEI);
		when(userRepository.findByEmail("joao@exemplo.com")).thenReturn(Optional.of(outroMei));

		assertThatThrownBy(() -> service.grant(meiId, "joao@exemplo.com"))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void deveRevogarAcessoAtivoDoProprioMei() {
		UUID contadorId = UUID.randomUUID();
		AccountantAccess ativo = new AccountantAccess(meiId, contadorId, CLOCK.instant().atOffset(ZoneOffset.UTC));
		UUID accessId = UUID.randomUUID();
		when(repository.findByIdAndMeiIdAndRevokedAtIsNull(accessId, meiId)).thenReturn(Optional.of(ativo));

		service.revoke(meiId, accessId);

		assertThat(ativo.isActive()).isFalse();
		assertThat(ativo.getRevokedAt().toInstant()).isEqualTo(CLOCK.instant());
		verify(repository).saveAndFlush(ativo);
	}

	@Test
	void deveResponder404AoRevogarAcessoInexistenteDeOutroMeiOuJaRevogado() {
		UUID accessId = UUID.randomUUID();
		when(repository.findByIdAndMeiIdAndRevokedAtIsNull(accessId, meiId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.revoke(meiId, accessId)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void hasActiveAccessConsultaOBancoACadaChamada() {
		UUID contadorId = UUID.randomUUID();
		when(repository.existsByMeiIdAndAccountantIdAndRevokedAtIsNull(meiId, contadorId))
				.thenReturn(true, false);

		assertThat(service.hasActiveAccess(contadorId, meiId)).isTrue();
		assertThat(service.hasActiveAccess(contadorId, meiId)).isFalse();
	}

	@Test
	void findActiveClientIdsListaSomenteOsMeisComAcessoAtivo() {
		UUID contadorId = UUID.randomUUID();
		UUID outroMei = UUID.randomUUID();
		when(repository.findByAccountantIdAndRevokedAtIsNull(contadorId)).thenReturn(List.of(
				new AccountantAccess(meiId, contadorId, CLOCK.instant().atOffset(ZoneOffset.UTC)),
				new AccountantAccess(outroMei, contadorId, CLOCK.instant().atOffset(ZoneOffset.UTC))));

		assertThat(service.findActiveClientIds(contadorId)).containsExactly(meiId, outroMei);
	}

	private User usuario(String nome, String email, Role role) {
		User usuario = new User(nome, email, "hash");
		ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(usuario, "role", role);
		return usuario;
	}
}
