package br.com.finamei.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finamei.auth.dto.RegisterUserRequest;
import br.com.finamei.category.CategoryService;
import br.com.finamei.shared.error.EmailAlreadyRegisteredException;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private CategoryService categoryService;

	@InjectMocks
	private RegisterUserService registerUserService;

	@Test
	void deveCadastrarUsuarioComEmailNormalizadoESenhaComHash() {
		RegisterUserRequest request = new RegisterUserRequest("Maria Silva", " Maria@Exemplo.com ", "senha123", null);
		when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
		when(passwordEncoder.encode("senha123")).thenReturn("hash-gerado");
		when(userRepository.save(any(User.class))).thenAnswer(chamada -> chamada.getArgument(0));

		User usuario = registerUserService.register(request);

		assertThat(usuario.getName()).isEqualTo("Maria Silva");
		assertThat(usuario.getEmail()).isEqualTo("maria@exemplo.com");
		assertThat(usuario.getPasswordHash()).isEqualTo("hash-gerado");
		verify(userRepository).save(any(User.class));
		verify(categoryService).createDefaultCategories(usuario.getId());
	}

	@Test
	void deveRejeitarCadastroQuandoEmailJaExiste() {
		RegisterUserRequest request = new RegisterUserRequest("Maria Silva", "maria@exemplo.com", "senha123", null);
		when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(true);

		assertThatThrownBy(() -> registerUserService.register(request))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
	}

	@Test
	void deveRejeitarCadastroQuandoBancoRecusaEmailDuplicadoConcorrente() {
		RegisterUserRequest request = new RegisterUserRequest("Maria Silva", "maria@exemplo.com", "senha123", null);
		when(userRepository.existsByEmail("maria@exemplo.com")).thenReturn(false);
		when(passwordEncoder.encode("senha123")).thenReturn("hash-gerado");
		when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("email duplicado"));

		assertThatThrownBy(() -> registerUserService.register(request))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
	}
}
