package br.com.finamei.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterChainTest {

	private static final String PROTECTED_PATH = "/api/v1/protected-resource-example";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void deveRejeitarAcessoARotaProtegidaSemToken() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void deveRejeitarAcessoComTokenMalformado() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void deveRejeitarAcessoComTokenExpirado() throws Exception {
		JwtService servicoComExpiracaoNoPassado = new JwtService(
				"test-secret-key-with-at-least-32-characters-1234567890", -1);
		User usuario = criarUsuario();
		String tokenExpirado = servicoComExpiracaoNoPassado.generateToken(usuario);

		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenExpirado))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void devePermitirQueTokenValidoPasseNoFiltroDeAutenticacao() throws Exception {
		User usuario = criarUsuario();
		String tokenValido = jwtService.generateToken(usuario);

		// Como não existe controller mapeado para este caminho, um token válido chega
		// à camada de roteamento (404) em vez de ser barrado pelo filtro de segurança (401).
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido))
				.andExpect(status().isNotFound());
	}

	@Test
	void devePermitirAcessoAoEndpointDeLoginSemAutenticacao() throws Exception {
		// GET não é o verbo mapeado (login é POST), mas o retorno 405 comprova que a
		// requisição passou pela camada de seguranca sem ser barrada com 401/403.
		mockMvc.perform(get("/api/v1/auth/login"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void devePermitirPreflightCorsDoFrontendLocal() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

	@Test
	void devePermitirPreflightCorsDoFrontendPublicadoNaVercel() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "https://finamei-ads-mackenzie.vercel.app")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://finamei-ads-mackenzie.vercel.app"));
	}

	@Test
	void deveRejeitarPreflightCorsDeOrigemNaoPermitida() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://origem-desconhecida.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	private User criarUsuario() {
		return userRepository.save(new User(
				"Maria Silva", "maria." + UUID.randomUUID() + "@exemplo.com", "hash"));
	}
}
