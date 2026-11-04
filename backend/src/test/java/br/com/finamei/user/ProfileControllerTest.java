package br.com.finamei.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileControllerTest {

	private static final String PROFILE_PATH = "/api/v1/users/me";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void deveConsultarOProprioPerfilSemExporHashDeSenha() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.perfil@exemplo.com");

		mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(maria.getId().toString()))
				.andExpect(jsonPath("$.name").value("Maria Silva"))
				.andExpect(jsonPath("$.email").value("maria.perfil@exemplo.com"))
				.andExpect(jsonPath("$.role").value("MEI"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void deveAtualizarNomeEEmailDoUsuarioAutenticado() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.antiga@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Maria Souza\", \"email\": \"Maria.Nova@Exemplo.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Maria Souza"))
				.andExpect(jsonPath("$.email").value("maria.nova@exemplo.com"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User salvo = userRepository.findById(maria.getId()).orElseThrow();
		assertThat(salvo.getName()).isEqualTo("Maria Souza");
		assertThat(salvo.getEmail()).isEqualTo("maria.nova@exemplo.com");
		assertThat(salvo.getPasswordHash()).isEqualTo("hash");
		assertThat(salvo.getRole()).isEqualTo(Role.MEI);
	}

	@Test
	void tokenEmitidoAntesDaTrocaDeEmailContinuaValidoPoisIdentificaPeloId() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.token@exemplo.com");
		String tokenAntigo = bearer(maria);

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, tokenAntigo)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Maria Silva\", \"email\": \"maria.token.novo@exemplo.com\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get(PROFILE_PATH).header(HttpHeaders.AUTHORIZATION, tokenAntigo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("maria.token.novo@exemplo.com"));
	}

	@Test
	void devePermitirSalvarMantendoOMesmoEmail() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.mesmo@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Maria Souza\", \"email\": \"maria.mesmo@exemplo.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Maria Souza"));
	}

	@Test
	void deveRecusarEmailJaUsadoPorOutraContaSemAlterarNada() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.conflito@exemplo.com");
		criarUsuario("João", "joao.conflito@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Outro Nome\", \"email\": \"Joao.Conflito@exemplo.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		User salvo = userRepository.findById(maria.getId()).orElseThrow();
		assertThat(salvo.getEmail()).isEqualTo("maria.conflito@exemplo.com");
		assertThat(salvo.getName()).isEqualTo("Maria Silva");
	}

	@Test
	void deveRejeitarDadosInvalidosComErrosPorCampo() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.invalida@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"\", \"email\": \"nao-e-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.name").exists())
				.andExpect(jsonPath("$.fieldErrors.email").exists());

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"" + "a".repeat(121) + "\", \"email\": \"maria.invalida@exemplo.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").value("O nome deve ter no máximo 120 caracteres."));
	}

	@Test
	void naoDeveAceitarAlteracaoDeSenhaOuPapelPeloPerfil() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.campos@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Maria\", \"email\": \"maria.campos@exemplo.com\","
								+ " \"role\": \"ADMIN\", \"passwordHash\": \"x\", \"id\": \"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("MEI"))
				.andExpect(jsonPath("$.id").value(maria.getId().toString()));

		User salvo = userRepository.findById(maria.getId()).orElseThrow();
		assertThat(salvo.getRole()).isEqualTo(Role.MEI);
		assertThat(salvo.getPasswordHash()).isEqualTo("hash");
	}

	@Test
	void deveAlterarSomenteOPerfilDoUsuarioDoToken() throws Exception {
		User maria = criarUsuario("Maria Silva", "maria.isolada@exemplo.com");
		User joao = criarUsuario("João", "joao.isolado@exemplo.com");

		mockMvc.perform(put(PROFILE_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(maria))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Maria Souza\", \"email\": \"maria.isolada@exemplo.com\"}"))
				.andExpect(status().isOk());

		User joaoSalvo = userRepository.findById(joao.getId()).orElseThrow();
		assertThat(joaoSalvo.getName()).isEqualTo("João");
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(PROFILE_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(put(PROFILE_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"A\", \"email\": \"a@exemplo.com\"}"))
				.andExpect(status().isUnauthorized());
	}

	private User criarUsuario(String nome, String email) {
		return userRepository.save(new User(nome, email, "hash"));
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
