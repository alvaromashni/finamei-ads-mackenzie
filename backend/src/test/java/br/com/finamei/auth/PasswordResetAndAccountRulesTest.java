package br.com.finamei.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.category.CategoryRepository;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** UC-002/RN10, conta inativa, perfil de contador, data de abertura e encerramento de sessão. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
class PasswordResetAndAccountRulesTest {

	private static final MutableClock RELOGIO = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
	private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

	@TestBean
	private Clock clock;

	static Clock clock() {
		return RELOGIO;
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@BeforeEach
	void reiniciarRelogio() {
		RELOGIO.set(Instant.parse("2026-11-17T12:00:00Z"));
	}

	@Test
	void redefinicaoTrocaASenhaEncerraSessaoENaoReusaOLink(CapturedOutput output) throws Exception {
		cadastrar("Ana Souza", "ana.reset@exemplo.com", "MEI");
		String tokenAntigo = login("ana.reset@exemplo.com", "senha123");

		String link = solicitar("ana.reset@exemplo.com", output);
		String token = extrairToken(link);

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "novaSenha1"}
								""".formatted(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Senha redefinida com sucesso. Entre com a nova senha."));

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ana.reset@exemplo.com", "password": "senha123"}
								"""))
				.andExpect(status().isUnauthorized());

		login("ana.reset@exemplo.com", "novaSenha1");

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "outraSenha1"}
								""".formatted(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_RESET_USED"))
				.andExpect(jsonPath("$.message")
						.value("Este link já foi utilizado. Solicite um novo link para redefinir a senha."));
	}

	@Test
	void emailNaoCadastradoRecebeAMesmaMensagemSemGerarLink(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-resets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"naoexiste@finamei.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.NEUTRAL_MESSAGE));

		assertThat(output.getAll()).doesNotContain("naoexiste@finamei.com");
	}

	@Test
	void linkExpiradoESenhaForaDaPoliticaSaoRecusados(CapturedOutput output) throws Exception {
		cadastrar("Ana Souza", "ana.expira@exemplo.com", "MEI");
		String token = extrairToken(solicitar("ana.expira@exemplo.com", output));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "curta1"}
								""".formatted(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password")
						.value("A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número."));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "semnumero"}
								""".formatted(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password")
						.value("A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número."));

		RELOGIO.advance(Duration.ofMinutes(31));

		mockMvc.perform(post("/api/v1/auth/password-resets/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token": "%s", "password": "novaSenha1"}
								""".formatted(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PASSWORD_RESET_EXPIRED"))
				.andExpect(jsonPath("$.message")
						.value("Este link expirou. Solicite um novo link para redefinir a senha."));
	}

	@Test
	void contaInativaInformaOSuporteEMantemASenhaErradaGenerica() throws Exception {
		cadastrar("Carlos", "carlos.inativo@exemplo.com", "MEI");
		userRepository.findByEmail("carlos.inativo@exemplo.com").orElseThrow().changeActive(false);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "carlos.inativo@exemplo.com", "password": "senha123"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
				.andExpect(jsonPath("$.message")
						.value("Esta conta está inativa. Entre em contato com o suporte para reativá-la."));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "carlos.inativo@exemplo.com", "password": "errada"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
	}

	@Test
	void cadastroDeContadorNaoCriaCategoriasEAdminERecusado() throws Exception {
		cadastrar("Bruno Contador", "bruno.contador@exemplo.com", "ACCOUNTANT");

		var contador = userRepository.findByEmail("bruno.contador@exemplo.com").orElseThrow();
		assertThat(contador.getRole().name()).isEqualTo("ACCOUNTANT");
		assertThat(categoryRepository.findAll())
				.noneMatch(category -> contador.getId().equals(category.getUserId()));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Admin", "email": "admin.publico@exemplo.com", "password": "senha123", "role": "ADMIN"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.role")
						.value("Escolha o perfil de microempreendedor ou de contador."));
	}

	@Test
	void perfilGravaADataDeAberturaEAplicaOLimiteProporcional() throws Exception {
		cadastrar("Ana Souza", "ana.abertura@exemplo.com", "MEI");
		String token = login("ana.abertura@exemplo.com", "senha123");

		mockMvc.perform(put("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Ana Souza", "email": "ana.abertura@exemplo.com", "meiOpeningDate": "2026-03-01"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meiOpeningDate").value("2026-03-01"));

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.meiOpeningDate").value("2026-03-01"));

		mockMvc.perform(get("/api/v1/revenue/summary").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.proportionalLimit").value(true))
				.andExpect(jsonPath("$.activeMonths").value(10))
				.andExpect(jsonPath("$.limit").value(67500.00));
	}

	@Test
	void loginInformaOAcessoAnterior() throws Exception {
		cadastrar("Ana Souza Doces", "ana.acesso@exemplo.com", "MEI");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ana.acesso@exemplo.com", "password": "senha123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.name").value("Ana Souza Doces"))
				.andExpect(jsonPath("$.lastAccessAt").value(nullValue()));

		RELOGIO.advance(Duration.ofHours(2));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ana.acesso@exemplo.com", "password": "senha123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastAccessAt").value("2026-11-17T12:00:00Z"));
	}

	private void cadastrar(String nome, String email, String role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "email": "%s", "password": "senha123", "role": "%s"}
								""".formatted(nome, email, role)))
				.andExpect(status().isCreated());
	}

	private String login(String email, String senha) throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "%s", "password": "%s"}
								""".formatted(email, senha)))
				.andExpect(status().isOk())
				.andReturn();
		String corpo = resultado.getResponse().getContentAsString();
		int inicio = corpo.indexOf("\"token\":\"") + "\"token\":\"".length();
		return corpo.substring(inicio, corpo.indexOf('"', inicio));
	}

	private String solicitar(String email, CapturedOutput output) throws Exception {
		int antes = output.getAll().length();
		mockMvc.perform(post("/api/v1/auth/password-resets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"%s\"}".formatted(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.NEUTRAL_MESSAGE));
		String trecho = output.getAll().substring(antes);
		assertThat(trecho).contains(email);
		return trecho;
	}

	private static String extrairToken(String texto) {
		Matcher matcher = TOKEN.matcher(texto);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
