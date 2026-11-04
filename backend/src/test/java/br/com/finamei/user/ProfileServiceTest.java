package br.com.finamei.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finamei.shared.error.EmailAlreadyRegisteredException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.dto.UpdateProfileRequest;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ProfileService profileService;

	@Test
	void deveAtualizarNomeEEmailNormalizado() throws Exception {
		User maria = usuario("Maria", "maria@exemplo.com");
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(userRepository.findByEmail("nova@exemplo.com")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(c -> c.getArgument(0));

		User atualizado = profileService.updateProfile(
				maria.getId(), new UpdateProfileRequest(" Maria Souza ", " Nova@Exemplo.com "));

		assertThat(atualizado.getName()).isEqualTo("Maria Souza");
		assertThat(atualizado.getEmail()).isEqualTo("nova@exemplo.com");
		assertThat(atualizado.getPasswordHash()).isEqualTo("hash");
	}

	@Test
	void devePermitirManterOProprioEmail() throws Exception {
		User maria = usuario("Maria", "maria@exemplo.com");
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(maria));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(c -> c.getArgument(0));

		User atualizado = profileService.updateProfile(
				maria.getId(), new UpdateProfileRequest("Maria Nova", "maria@exemplo.com"));

		assertThat(atualizado.getName()).isEqualTo("Maria Nova");
	}

	@Test
	void deveRecusarEmailDeOutroUsuario() throws Exception {
		User maria = usuario("Maria", "maria@exemplo.com");
		User joao = usuario("João", "joao@exemplo.com");
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(userRepository.findByEmail("joao@exemplo.com")).thenReturn(Optional.of(joao));

		assertThatThrownBy(() -> profileService.updateProfile(
				maria.getId(), new UpdateProfileRequest("Maria", "JOAO@exemplo.com")))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
		verify(userRepository, never()).saveAndFlush(any());
		assertThat(maria.getEmail()).isEqualTo("maria@exemplo.com");
	}

	@Test
	void deveTraduzirViolacaoDeUnicidadeDoBancoEmConflito() throws Exception {
		User maria = usuario("Maria", "maria@exemplo.com");
		when(userRepository.findById(maria.getId())).thenReturn(Optional.of(maria));
		when(userRepository.findByEmail("nova@exemplo.com")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uk"));

		assertThatThrownBy(() -> profileService.updateProfile(
				maria.getId(), new UpdateProfileRequest("Maria", "nova@exemplo.com")))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
	}

	@Test
	void deveResponder404QuandoUsuarioNaoExiste() {
		UUID id = UUID.randomUUID();
		when(userRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.getProfile(id)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> profileService.updateProfile(id, new UpdateProfileRequest("A", "a@b.com")))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private User usuario(String nome, String email) throws Exception {
		User usuario = new User(nome, email, "hash");
		Field campo = User.class.getDeclaredField("id");
		campo.setAccessible(true);
		campo.set(usuario, UUID.randomUUID());
		return usuario;
	}
}
