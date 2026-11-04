package br.com.finamei.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.accountantaccess.AccountantAccessService;
import br.com.finamei.accountantaccess.dto.AccountantAccessResponse;
import br.com.finamei.category.CategoryRepository;
import br.com.finamei.category.CategoryService;
import br.com.finamei.das.DasGuideRepository;
import br.com.finamei.transaction.Transaction;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * ONF03 a ONF05 (issue #37): cenários de acesso indevido consolidados. O usuário B tenta ler,
 * alterar e excluir recursos do usuário A em cada módulo, e tokens inválidos são recusados.
 * Os testes de cada módulo também cobrem o próprio isolamento; este reúne tudo em um só lugar
 * como evidência do checklist de segurança.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataIsolationTest {

	private static final String TRANSACTIONS_PATH = "/api/v1/transactions";

	@Value("${app.jwt.secret}")
	private String secret;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private DasGuideRepository dasGuideRepository;

	@Autowired
	private AccountantAccessService accessService;

	private User alice;
	private User bruno;
	private UUID categoriaDaAlice;
	private UUID lancamentoDaAlice;

	@BeforeEach
	void criarUsuarios() {
		alice = userRepository.save(new User("Alice", "alice.isolamento@exemplo.com", "hash"));
		bruno = userRepository.save(new User("Bruno", "bruno.isolamento@exemplo.com", "hash"));
		categoryService.createDefaultCategories(alice.getId());
		categoryService.createDefaultCategories(bruno.getId());
		categoriaDaAlice = receitaDe(alice);
		lancamentoDaAlice = transactionRepository.save(new Transaction(
				alice.getId(), categoriaDaAlice, TransactionType.INCOME, new BigDecimal("500.00"),
				LocalDate.now(), "Venda da Alice")).getId();
	}

	// ---------- Lançamentos ----------

	@Test
	void usuarioBNaoAlteraNemExcluiLancamentoDoUsuarioA() throws Exception {
		String corpo = """
				{"type": "INCOME", "amount": 1.00, "date": "%s", "categoryId": "%s", "description": "invasao"}
				""".formatted(LocalDate.now(), receitaDe(bruno));

		mockMvc.perform(put(TRANSACTIONS_PATH + "/" + lancamentoDaAlice)
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno))
						.contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + lancamentoDaAlice)
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		Transaction intacto = transactionRepository.findById(lancamentoDaAlice).orElseThrow();
		assertThat(intacto.getDescription()).isEqualTo("Venda da Alice");
		assertThat(intacto.isDeleted()).isFalse();
	}

	@Test
	void usuarioBNaoVeLancamentosNemSaldoDoUsuarioA() throws Exception {
		mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get(TRANSACTIONS_PATH + "/summary").param("month", LocalDate.now().toString().substring(0, 7))
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.income").value(0));
		mockMvc.perform(get("/api/v1/revenue/summary").param("year", String.valueOf(LocalDate.now().getYear()))
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accumulated").value(0));
	}

	@Test
	void usuarioBNaoLancaUsandoCategoriaDoUsuarioA() throws Exception {
		mockMvc.perform(post(TRANSACTIONS_PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "INCOME", "amount": 10.00, "date": "%s", "categoryId": "%s", "description": "x"}
								""".formatted(LocalDate.now(), categoriaDaAlice)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.categoryId").exists());

		assertThat(transactionRepository.findAll()).hasSize(1);
	}

	// ---------- Categorias ----------

	@Test
	void usuarioBNaoRenomeiaNemInativaCategoriaDoUsuarioA() throws Exception {
		String nomeOriginal = categoryRepository.findById(categoriaDaAlice).orElseThrow().getName();

		mockMvc.perform(put("/api/v1/categories/" + categoriaDaAlice)
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Invadida\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/categories/" + categoriaDaAlice + "/status")
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno))
						.contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
				.andExpect(status().isNotFound());

		var categoria = categoryRepository.findById(categoriaDaAlice).orElseThrow();
		assertThat(categoria.getName()).isEqualTo(nomeOriginal);
		assertThat(categoria.isActive()).isTrue();
	}

	@Test
	void listaDeCategoriasDoUsuarioBNaoTrazCategoriasDoUsuarioA() throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(corpo).doesNotContain(categoriaDaAlice.toString());
	}

	// ---------- DAS ----------

	@Test
	void usuarioBNaoPagaNemDesfazPagamentoDeGuiaDoUsuarioA() throws Exception {
		String guiaDaAlice = idsDasGuias(alice).get(0);

		mockMvc.perform(post("/api/v1/das/" + guiaDaAlice + "/payment")
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno))
						.contentType(MediaType.APPLICATION_JSON).content("{\"paidAt\": \"2026-01-10\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/das/" + guiaDaAlice + "/payment")
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isNotFound());

		assertThat(dasGuideRepository.findById(UUID.fromString(guiaDaAlice)).orElseThrow().getPaidAt()).isNull();
	}

	@Test
	void listaDeGuiasDoUsuarioBNaoTrazGuiasDoUsuarioA() throws Exception {
		List<String> idsDaAlice = idsDasGuias(alice);
		List<String> idsDoBruno = idsDasGuias(bruno);

		assertThat(idsDoBruno).isNotEmpty().doesNotContainAnyElementsOf(idsDaAlice);
	}

	// ---------- Perfil ----------

	@Test
	void perfilRetornaSempreOUsuarioAutenticado() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("bruno.isolamento@exemplo.com"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	// ---------- Acesso do contador ----------

	@Test
	void usuarioBNaoRevogaNemVeAcessoConcedidoPeloUsuarioA() throws Exception {
		User contador = criarContador("contador.isolamento@contabil.com");
		AccountantAccessResponse acesso = accessService.grant(alice.getId(), contador.getEmail());

		mockMvc.perform(delete("/api/v1/accountant-access/" + acesso.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/accountant-access").header(HttpHeaders.AUTHORIZATION, bearer(bruno)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		assertThat(accessService.hasActiveAccess(contador.getId(), alice.getId())).isTrue();
		assertThat(accessService.hasActiveAccess(contador.getId(), bruno.getId())).isFalse();
	}

	@Test
	void contadorNaoGerenciaAcessosNemEscreveNemLeDadosDoMei() throws Exception {
		User contador = criarContador("contador.leitura@contabil.com");
		accessService.grant(alice.getId(), contador.getEmail());

		mockMvc.perform(get("/api/v1/accountant-access").header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/accountant-access")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador))
						.contentType(MediaType.APPLICATION_JSON).content("{\"accountantEmail\": \"x@y.com\"}"))
				.andExpect(status().isForbidden());
		// Mesmo com vínculo ativo, as consultas comuns devolvem só os dados do próprio contador.
		mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)));
		// Somente leitura (ONF05): escrita é 403.
		mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + lancamentoDaAlice)
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden());
	}

	// ---------- Autenticação e token ----------

	@Test
	void requisicaoSemTokenRecebe401EmTodosOsModulos() throws Exception {
		MockHttpServletRequestBuilder[] requisicoes = {
				get(TRANSACTIONS_PATH), put(TRANSACTIONS_PATH + "/" + lancamentoDaAlice),
				delete(TRANSACTIONS_PATH + "/" + lancamentoDaAlice), get("/api/v1/categories"),
				get("/api/v1/das"), post("/api/v1/das/" + UUID.randomUUID() + "/payment"),
				get("/api/v1/accountant-access"), get("/api/v1/users/me"), get("/api/v1/revenue/summary")};

		for (MockHttpServletRequestBuilder requisicao : requisicoes) {
			mockMvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}
	}

	@Test
	void tokenExpiradoRecebe401() throws Exception {
		// Mesmo segredo do contexto: a recusa só pode vir da expiração.
		String token = new JwtService(secret, -1).generateToken(alice);

		mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void tokenComAssinaturaInvalidaRecebe401() throws Exception {
		String token = new JwtService("outra-chave-secreta-com-pelo-menos-32-caracteres", 30).generateToken(alice);

		mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSemAssinaturaAlgNoneRecebe401() throws Exception {
		String token = Jwts.builder().subject(alice.getId().toString()).claim("role", "ADMIN").compact();

		mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenComPayloadAdulteradoRecebe401() throws Exception {
		String[] partes = jwtService.generateToken(alice).split("\\.");
		String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"" + bruno.getId() + "\",\"role\":\"MEI\",\"exp\":9999999999}")
						.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(get(TRANSACTIONS_PATH)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + partes[0] + "." + payloadFalso + "." + partes[2]))
				.andExpect(status().isUnauthorized());
	}

	private UUID receitaDe(User usuario) {
		return categoryRepository.findByUserIdAndTypeOrderByNameAsc(usuario.getId(), TransactionType.INCOME)
				.get(0).getId();
	}

	private List<String> idsDasGuias(User usuario) throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/das").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$[*].id");
	}

	private User criarContador(String email) {
		User contador = new User("Contador", email, "hash");
		ReflectionTestUtils.setField(contador, "role", Role.ACCOUNTANT);
		return userRepository.save(contador);
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
