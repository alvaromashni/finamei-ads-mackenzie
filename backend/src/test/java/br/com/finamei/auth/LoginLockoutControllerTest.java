package br.com.finamei.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** RN09 de ponta a ponta (HTTP + banco), com o tempo controlado por um relógio de teste. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginLockoutControllerTest {

	private static final MutableClock RELOGIO = new MutableClock(Instant.parse("2026-09-15T12:00:00Z"));

	@TestBean
	private Clock clock;

	static Clock clock() {
		return RELOGIO;
	}

	private static final String CADASTRO_JSON = """
			{"name": "Maria Silva", "email": "%s", "password": "senha123"}
			""";

	private static final String LOGIN_JSON = """
			{"email": "%s", "password": "%s"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@BeforeEach
	void registrarUsuario() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CADASTRO_JSON.formatted("maria.bloqueio@exemplo.com")))
				.andExpect(status().isCreated());
	}

	@Test
	void deveBloquearNaQuintaFalhaERecusarMesmoASenhaCorreta() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 4);

		mockMvc.perform(login("maria.bloqueio@exemplo.com", "errada"))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
				.andExpect(header().string("Retry-After", "900"));

		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123"))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	void deveLiberarOLoginDepoisDe15MinutosSemEsperaReal() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 5);

		RELOGIO.advance(Duration.ofMinutes(14));
		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123"))
				.andExpect(status().isLocked())
				.andExpect(header().string("Retry-After", "60"));

		RELOGIO.advance(Duration.ofMinutes(1));
		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void naoDeveBloquearAntesDaQuintaFalha() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 4);

		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123"))
				.andExpect(status().isOk());
	}

	@Test
	void loginCorretoDeveZerarAsTentativasAcumuladas() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 4);
		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123")).andExpect(status().isOk());
		assertThat(loginAttemptRepository.findByEmail("maria.bloqueio@exemplo.com")).isEmpty();

		// Se a contagem não tivesse zerado, a 1a falha abaixo já bloquearia.
		falhar("maria.bloqueio@exemplo.com", 4);
		mockMvc.perform(login("maria.bloqueio@exemplo.com", "senha123")).andExpect(status().isOk());
	}

	@Test
	void deveRecomecarAContagemDepoisQueOBloqueioExpira() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 5);
		RELOGIO.advance(Duration.ofMinutes(15));

		mockMvc.perform(login("maria.bloqueio@exemplo.com", "errada"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void emailInexistenteDeveSeComportarIgualAUmExistente() throws Exception {
		for (int i = 0; i < 4; i++) {
			mockMvc.perform(login("fantasma@exemplo.com", "qualquer"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
					.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
		}

		mockMvc.perform(login("fantasma@exemplo.com", "qualquer"))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	void bloqueioDeUmEmailNaoAfetaOutro() throws Exception {
		falhar("maria.bloqueio@exemplo.com", 5);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CADASTRO_JSON.formatted("joao.bloqueio@exemplo.com")))
				.andExpect(status().isCreated());
		mockMvc.perform(login("joao.bloqueio@exemplo.com", "senha123")).andExpect(status().isOk());
	}

	private void falhar(String email, int vezes) throws Exception {
		for (int i = 0; i < vezes; i++) {
			mockMvc.perform(login(email, "errada"));
		}
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
			String email, String senha) {
		return post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(LOGIN_JSON.formatted(email, senha));
	}
}
