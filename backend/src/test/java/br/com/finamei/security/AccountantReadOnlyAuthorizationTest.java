package br.com.finamei.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** ONF05: o contador nunca cria, altera ou exclui dados financeiros. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountantReadOnlyAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void contadorNaoPodeEscreverEmLancamentos() throws Exception {
		String token = tokenDeContador("ana.lancamentos@contabil.com");
		String id = UUID.randomUUID().toString();

		assertProibido(post("/api/v1/transactions"), token);
		assertProibido(put("/api/v1/transactions/" + id), token);
		assertProibido(patch("/api/v1/transactions/" + id), token);
		assertProibido(delete("/api/v1/transactions/" + id), token);
	}

	@Test
	void contadorNaoPodeEscreverEmCategorias() throws Exception {
		String token = tokenDeContador("ana.categorias@contabil.com");
		String id = UUID.randomUUID().toString();

		assertProibido(post("/api/v1/categories"), token);
		assertProibido(put("/api/v1/categories/" + id), token);
		assertProibido(patch("/api/v1/categories/" + id), token);
		assertProibido(patch("/api/v1/categories/" + id + "/status"), token);
		assertProibido(delete("/api/v1/categories/" + id), token);
	}

	@Test
	void contadorNaoPodeRegistrarNemDesfazerPagamentoDeDas() throws Exception {
		String token = tokenDeContador("ana.das@contabil.com");
		String id = UUID.randomUUID().toString();

		assertProibido(post("/api/v1/das/" + id + "/payment"), token);
		assertProibido(delete("/api/v1/das/" + id + "/payment"), token);
	}

	@Test
	void mei_naoEBloqueadoPelaRegraDeSomenteLeitura() throws Exception {
		User mei = userRepository.save(new User("Maria", "maria.escrita@exemplo.com", "hash"));
		String token = "Bearer " + jwtService.generateToken(mei);

		// Corpo vazio: passa pela autorização e é recusado só pela validação (400), nunca por 403.
		mockMvc.perform(post("/api/v1/transactions")
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/categories")
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void leituraNaoEBloqueadaPelaRegraDeSomenteLeitura() throws Exception {
		String token = tokenDeContador("ana.leitura@contabil.com");

		// A regra só atinge métodos de escrita; as consultas dependem do vínculo (#33).
		mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk());
	}

	private void assertProibido(MockHttpServletRequestBuilder requisicao, String token) throws Exception {
		mockMvc.perform(requisicao
						.header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	private String tokenDeContador(String email) {
		User contador = new User("Ana Contabilidade", email, "hash");
		ReflectionTestUtils.setField(contador, "role", Role.ACCOUNTANT);
		contador = userRepository.save(contador);
		return "Bearer " + jwtService.generateToken(contador);
	}
}
