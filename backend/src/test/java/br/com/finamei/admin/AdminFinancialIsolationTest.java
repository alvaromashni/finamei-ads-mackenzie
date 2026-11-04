package br.com.finamei.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.category.CategoryRepository;
import br.com.finamei.category.CategoryService;
import br.com.finamei.security.JwtService;
import br.com.finamei.transaction.Transaction;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * O papel ADMIN não dá acesso implícito aos dados financeiros: lançamentos, categorias, DAS e
 * faturamento continuam filtrados pelo id do próprio token, então o administrador só enxerga
 * (e altera) os dados dele, nunca os de um MEI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminFinancialIsolationTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(LocalDate.of(2026, 9, 15).atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
	}

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

	private User admin;
	private User mei;
	private Transaction lancamentoDoMei;
	private UUID categoriaDoMei;

	@BeforeEach
	void prepararDados() {
		admin = criar("Admin", "admin.isola@exemplo.com", Role.ADMIN);
		mei = criar("Maria", "maria.isola@exemplo.com", Role.MEI);
		categoryService.createDefaultCategories(mei.getId());
		categoryService.createDefaultCategories(admin.getId());
		categoriaDoMei = categoryRepository.findByUserIdOrderByTypeAscNameAsc(mei.getId()).stream()
				.filter(categoria -> categoria.getName().equals("Venda de produtos"))
				.findFirst().orElseThrow().getId();
		lancamentoDoMei = transactionRepository.saveAndFlush(new Transaction(mei.getId(), categoriaDoMei,
				TransactionType.INCOME, new BigDecimal("1234.56"), LocalDate.of(2026, 9, 3), "Venda da Maria"));
	}

	@Test
	void administradorNaoEnxergaLancamentosDeUmMei() throws Exception {
		mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(jsonPath("$.content", hasSize(1)));
	}

	@Test
	void administradorNaoEnxergaSaldoNemFaturamentoDeUmMei() throws Exception {
		mockMvc.perform(get("/api/v1/transactions/summary").param("month", "2026-09")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.income").value(0))
				.andExpect(jsonPath("$.expense").value(0));

		mockMvc.perform(get("/api/v1/revenue/summary").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accumulated").value(0));

		mockMvc.perform(get("/api/v1/revenue/summary").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(jsonPath("$.accumulated").value(1234.56));
	}

	@Test
	void administradorNaoAlteraNemApagaLancamentoDeUmMei() throws Exception {
		mockMvc.perform(put("/api/v1/transactions/" + lancamentoDoMei.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "INCOME", "amount": 1, "date": "2026-09-03",
								 "categoryId": "%s", "description": "Adulterado"}
								""".formatted(categoriaDoMei)))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/transactions/" + lancamentoDoMei.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/transactions/summary").param("month", "2026-09")
						.header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(jsonPath("$.income").value(1234.56));
	}

	@Test
	void administradorNaoEnxergaCategoriasNemGuiasDasDeUmMei() throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain(categoriaDoMei.toString());

		mockMvc.perform(get("/api/v1/das").param("year", "2026").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(12)));
		// As guias do administrador são dele: pagar a guia de um MEI não é possível pelo id.
		mockMvc.perform(delete("/api/v1/das/" + UUID.randomUUID() + "/payment")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());
	}

	private User criar(String nome, String email, Role role) {
		User usuario = new User(nome, email, "hash");
		ReflectionTestUtils.setField(usuario, "role", role);
		return userRepository.save(usuario);
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
