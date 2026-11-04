package br.com.finamei.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.security.JwtService;
import br.com.finamei.transaction.Transaction;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerTest {

	private static final String PATH = "/api/v1/reports";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private JdbcClient jdbcClient;

	private UUID categoriaId;

	@BeforeEach
	void criarCategoria() {
		categoriaId = UUID.randomUUID();
		jdbcClient.sql("INSERT INTO categories (id, name, type) VALUES (:id, 'Vendas', 'INCOME')")
				.param("id", categoriaId)
				.update();
	}

	@Test
	void relatorioMensalDeveConferirComLancamentosConhecidosSemVazarMesesVizinhos() throws Exception {
		User maria = usuario("maria.rel.mensal@exemplo.com");
		User joao = usuario("joao.rel.mensal@exemplo.com");

		lancar(maria, TransactionType.INCOME, "1000.00", LocalDate.of(2026, 3, 1));
		lancar(maria, TransactionType.INCOME, "500.50", LocalDate.of(2026, 3, 31));
		lancar(maria, TransactionType.EXPENSE, "200.25", LocalDate.of(2026, 3, 15));
		// Vizinhos: último dia de fevereiro e primeiro de abril ficam de fora.
		lancar(maria, TransactionType.INCOME, "9999.00", LocalDate.of(2026, 2, 28));
		lancar(maria, TransactionType.EXPENSE, "8888.00", LocalDate.of(2026, 4, 1));
		Transaction excluida = lancar(maria, TransactionType.INCOME, "7777.00", LocalDate.of(2026, 3, 10));
		excluida.markAsDeleted();
		lancar(joao, TransactionType.INCOME, "5555.00", LocalDate.of(2026, 3, 10));
		transactionRepository.flush();

		mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026").param("month", "3")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period").value("MONTHLY"))
				.andExpect(jsonPath("$.year").value(2026))
				.andExpect(jsonPath("$.month").value(3))
				.andExpect(jsonPath("$.income").value(1500.50))
				.andExpect(jsonPath("$.expense").value(200.25))
				.andExpect(jsonPath("$.balance").value(1300.25))
				.andExpect(jsonPath("$.transactionCount").value(3))
				// Faturamento do ano inteiro: 1000 + 500,50 + 9999 (receitas ativas do usuário em 2026).
				.andExpect(jsonPath("$.revenue.year").value(2026))
				.andExpect(jsonPath("$.revenue.accumulated").value(11499.50))
				.andExpect(jsonPath("$.revenue.band").value("NORMAL"));
	}

	@Test
	void relatorioAnualDeveSomarOAnoTodoEIgnorarOutrosAnos() throws Exception {
		User maria = usuario("maria.rel.anual@exemplo.com");
		lancar(maria, TransactionType.INCOME, "100.00", LocalDate.of(2026, 1, 1));
		lancar(maria, TransactionType.INCOME, "200.00", LocalDate.of(2026, 12, 31));
		lancar(maria, TransactionType.EXPENSE, "50.00", LocalDate.of(2026, 6, 6));
		lancar(maria, TransactionType.INCOME, "999.00", LocalDate.of(2025, 12, 31));
		lancar(maria, TransactionType.EXPENSE, "999.00", LocalDate.of(2027, 1, 1));

		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period").value("ANNUAL"))
				.andExpect(jsonPath("$.month").doesNotExist())
				.andExpect(jsonPath("$.income").value(300.00))
				.andExpect(jsonPath("$.expense").value(50.00))
				.andExpect(jsonPath("$.balance").value(250.00))
				.andExpect(jsonPath("$.transactionCount").value(3))
				.andExpect(jsonPath("$.revenue.accumulated").value(300.00));
	}

	@Test
	void relatorioDeUsuarioSemLancamentosDeveSerVazio() throws Exception {
		User maria = usuario("maria.rel.vazio@exemplo.com");
		User joao = usuario("joao.rel.vazio@exemplo.com");
		lancar(joao, TransactionType.INCOME, "5555.00", LocalDate.of(2026, 3, 10));

		mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026").param("month", "3")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.income").value(0))
				.andExpect(jsonPath("$.expense").value(0))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.transactionCount").value(0))
				.andExpect(jsonPath("$.revenue.accumulated").value(0));
	}

	@Test
	void deveRejeitarMesInvalidoComErroPadronizado() throws Exception {
		User maria = usuario("maria.rel.mes@exemplo.com");

		mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026").param("month", "13")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.month").exists());
	}

	@Test
	void deveExigirMesNoRelatorioMensal() throws Exception {
		User maria = usuario("maria.rel.semmes@exemplo.com");

		mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.month").exists());
	}

	@Test
	void deveRejeitarAnoForaDoIntervalo() throws Exception {
		User maria = usuario("maria.rel.ano@exemplo.com");

		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "1800")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.year").exists());
	}

	@Test
	void deveRejeitarPeriodoDesconhecidoOuAusenteEParametrosMalFormados() throws Exception {
		User maria = usuario("maria.rel.periodo@exemplo.com");
		String auth = bearer(maria);

		mockMvc.perform(get(PATH).param("period", "WEEKLY").param("year", "2026").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.period").exists());
		mockMvc.perform(get(PATH).param("year", "2026").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.period").exists());
		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "abc").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.year").exists());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	private User usuario(String email) {
		return userRepository.save(new User("Usuário Teste", email, "hash"));
	}

	private Transaction lancar(User usuario, TransactionType tipo, String valor, LocalDate data) {
		return transactionRepository.save(
				new Transaction(usuario.getId(), categoriaId, tipo, new BigDecimal(valor), data, "Lançamento de teste"));
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
