package br.com.finamei.performance;

import br.com.finamei.category.CategoryService;
import br.com.finamei.das.DasService;
import br.com.finamei.revenue.RevenueService;
import br.com.finamei.transaction.TransactionFilter;
import br.com.finamei.transaction.TransactionService;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Evidência de performance (ONF06, ONF07): conta as instruções SQL de cada consulta mais usada
 * com volume representativo (cerca de 40 mil lançamentos, 20 mil de um único usuário) e mostra
 * o plano de execução das consultas pesadas. Números e análise em docs/performance.md.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryPerformanceTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final int LANCAMENTOS_DO_USUARIO_PESADO = 20_000;
	private static final int USUARIOS_DE_RUIDO = 5;
	private static final int LANCAMENTOS_POR_USUARIO_DE_RUIDO = 4_000;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(LocalDate.of(2026, 9, 15).atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
	}

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private RevenueService revenueService;

	@Autowired
	private DasService dasService;

	private Statistics statistics;
	private final List<UUID> usuarios = new ArrayList<>();
	private User pesado;

	@BeforeAll
	void popularVolume() {
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		pesado = criarUsuarioComLancamentos("pesado", LANCAMENTOS_DO_USUARIO_PESADO);
		for (int i = 0; i < USUARIOS_DE_RUIDO; i++) {
			criarUsuarioComLancamentos("ruido" + i, LANCAMENTOS_POR_USUARIO_DE_RUIDO);
		}
		jdbcTemplate.execute("ANALYZE");
	}

	@AfterAll
	void limparVolume() {
		for (UUID id : usuarios) {
			jdbcTemplate.update("delete from das_guides where user_id = ?", id);
			jdbcTemplate.update("delete from transactions where user_id = ?", id);
			jdbcTemplate.update("delete from categories where user_id = ?", id);
		}
		for (UUID id : usuarios) {
			jdbcTemplate.update("delete from users where id = ?", id);
		}
	}

	@Test
	void medirConsultasMaisUsadas() {
		System.out.println("=== QUERY-COUNT (instrucoes SQL por chamada) ===");

		// Página cheia (100 lançamentos de ~12 categorias): se houvesse N+1, seriam ~100 instruções.
		assertThat(medir("Listagem de lançamentos, pagina de 100 (sem filtro)", () ->
				transactionService.list(pesado.getId(), 0, 100, new TransactionFilter(null, null, null, null, null))))
				.as("contagem + página + categorias em lote").isLessThanOrEqualTo(3);
		assertThat(medir("Listagem de lançamentos, pagina de 20 com filtros (mes + tipo)", () ->
				transactionService.list(pesado.getId(), 0, 20, new TransactionFilter(
						LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransactionType.INCOME, null, null))))
				.isLessThanOrEqualTo(3);
		assertThat(medir("Resumo de saldo do mes", () -> transactionService.summary(pesado.getId(), YearMonth.of(2026, 8))))
				.as("uma consulta agregada").isEqualTo(1);
		assertThat(medir("Faturamento anual", () -> revenueService.getAnnualSummary(pesado.getId(), 2026)))
				.as("soma do ano + data de abertura do MEI").isLessThanOrEqualTo(2);
		assertThat(medir("Categorias do usuario", () -> categoryService.list(pesado.getId(), null)))
				.isEqualTo(1);

		User novo = criarUsuario("das");
		assertThat(medir("DAS do ano corrente, 1o acesso (cria 12 guias)", () -> dasService.listGuides(novo.getId(), 2026)))
				.as("busca + inserção em lote das 12 guias").isLessThanOrEqualTo(3);
		assertThat(medir("DAS do ano corrente, acessos seguintes", () -> dasService.listGuides(novo.getId(), 2026)))
				.isEqualTo(1);

		System.out.println("=== EXPLAIN ===");
		explicar("Listagem (pagina)", """
				select * from transactions
				where user_id = ? and deleted = false and date >= ? and date <= ?
				order by date desc, created_at desc limit 20
				""", pesado.getId(), Date.valueOf("2026-08-01"), Date.valueOf("2026-08-31"));
		explicar("Soma por tipo e periodo", """
				select sum(amount) from transactions
				where user_id = ? and type = 'INCOME' and date between ? and ? and deleted = false
				""", pesado.getId(), Date.valueOf("2026-01-01"), Date.valueOf("2026-12-31"));
		explicar("Saldo total por tipo", """
				select sum(amount) from transactions where user_id = ? and type = 'INCOME' and deleted = false
				""", pesado.getId());
	}

	/** Executa a consulta, imprime o resultado e devolve quantas instruções SQL ela disparou. */
	private long medir(String nome, Supplier<?> consulta) {
		statistics.clear();
		consulta.get();
		long instrucoes = statistics.getPrepareStatementCount();
		System.out.printf("QUERYCOUNT | %-62s | %d instrucoes | mediana %.2f ms%n",
				nome, instrucoes, medianaEmMs(consulta));
		return instrucoes;
	}

	@Test
	void listagemNuncaRetornaColecaoIlimitada() {
		var pagina = transactionService.list(
				pesado.getId(), 0, 1_000_000, new TransactionFilter(null, null, null, null, null));

		assertThat(pagina.content()).hasSize(100);
		assertThat(pagina.totalElements()).isGreaterThan(10_000);
	}

	@Test
	void consultasDeFiltroEPeriodoUsamOIndiceDeUsuarioEData() {
		String planoListagem = String.join("\n", jdbcTemplate.queryForList("EXPLAIN " + """
				select * from transactions
				where user_id = ? and deleted = false and date >= ? and date <= ?
				order by date desc, created_at desc limit 20
				""", String.class, pesado.getId(), Date.valueOf("2026-08-01"), Date.valueOf("2026-08-31")));
		assertThat(planoListagem).contains("IX_TRANSACTIONS_USER_DATE");
	}

	/** Mediana de 15 execuções após 5 de aquecimento. Útil só para comparar antes/depois no mesmo ambiente. */
	private double medianaEmMs(Supplier<?> consulta) {
		for (int i = 0; i < 5; i++) {
			consulta.get();
		}
		double[] tempos = new double[15];
		for (int i = 0; i < tempos.length; i++) {
			long inicio = System.nanoTime();
			consulta.get();
			tempos[i] = (System.nanoTime() - inicio) / 1_000_000.0;
		}
		java.util.Arrays.sort(tempos);
		return tempos[tempos.length / 2];
	}

	private void explicar(String nome, String sql, Object... parametros) {
		List<String> plano = jdbcTemplate.queryForList("EXPLAIN " + sql, String.class, parametros);
		System.out.println("PLAN | " + nome + "\n" + String.join("\n", plano).replace("\n", "\n     "));
	}

	private User criarUsuario(String apelido) {
		User usuario = userRepository.save(
				new User("Usuário " + apelido, apelido + "." + UUID.randomUUID() + "@perf.exemplo.com", "hash"));
		usuarios.add(usuario.getId());
		categoryService.createDefaultCategories(usuario.getId());
		return usuario;
	}

	private User criarUsuarioComLancamentos(String apelido, int quantidade) {
		User usuario = criarUsuario(apelido);
		List<UUID> receitas = categoriasDoTipo(usuario, TransactionType.INCOME);
		List<UUID> despesas = categoriasDoTipo(usuario, TransactionType.EXPENSE);
		Random random = new Random(42);
		LocalDate inicio = LocalDate.of(2024, 1, 1);

		List<Object[]> linhas = new ArrayList<>(quantidade);
		for (int i = 0; i < quantidade; i++) {
			boolean receita = random.nextInt(100) < 40;
			List<UUID> categorias = receita ? receitas : despesas;
			LocalDate data = inicio.plusDays(random.nextInt(980));
			linhas.add(new Object[] {
					UUID.randomUUID(), usuario.getId(), categorias.get(random.nextInt(categorias.size())),
					receita ? "INCOME" : "EXPENSE", java.math.BigDecimal.valueOf(1 + random.nextInt(50_000), 2),
					Date.valueOf(data), "Lançamento " + i, random.nextInt(100) < 3,
					java.sql.Timestamp.valueOf(data.atStartOfDay().plusSeconds(i % 86_000)),
			});
		}
		jdbcTemplate.batchUpdate("""
				insert into transactions (id, user_id, category_id, type, amount, date, description, deleted,
				                          created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", linhas.stream().map(l -> new Object[] {
						l[0], l[1], l[2], l[3], l[4], l[5], l[6], l[7], l[8], l[8] }).toList());
		return usuario;
	}

	private List<UUID> categoriasDoTipo(User usuario, TransactionType tipo) {
		return categoryService.list(usuario.getId(), tipo).stream().map(c -> c.id()).toList();
	}
}
