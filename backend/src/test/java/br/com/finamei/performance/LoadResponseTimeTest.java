package br.com.finamei.performance;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finamei.category.CategoryService;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Teste de carga leve (ONF06, ONF07). Sobe a aplicação numa porta real, cria cerca de 10 mil
 * lançamentos para um usuário e mede, por HTTP, as operações críticas com várias repetições.
 *
 * <p>Fica fora do {@code verify} padrão (tag {@code load}); rode com {@code ./mvnw test -Pload-test}.
 * Procedimento, ambiente e resultados em docs/teste-de-carga.md. Parâmetros (propriedades de sistema):
 * {@code load.transactions} (10000), {@code load.reps} (50), {@code load.threads} (5),
 * {@code load.p95Ms} (2000) e {@code load.annualMs} (5000).
 *
 * <p>Por padrão usa o H2 do perfil de testes; para PostgreSQL passe {@code -Dspring.datasource.url=...},
 * {@code -Dspring.datasource.username=...}, {@code -Dspring.datasource.password=...} e
 * {@code -Dspring.datasource.driver-class-name=org.postgresql.Driver}.
 */
@Tag("load")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoadResponseTimeTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final LocalDate HOJE = LocalDate.of(2026, 9, 15);
	private static final LocalDate PRIMEIRO_DIA = LocalDate.of(2025, 1, 1);
	private static final String SENHA = "SenhaDeCarga#1";
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	private static final int TRANSACOES = Integer.getInteger("load.transactions", 10_000);
	private static final int REPETICOES = Integer.getInteger("load.reps", 50);
	private static final int THREADS = Integer.getInteger("load.threads", 5);
	private static final long LIMITE_P95_MS = Long.getLong("load.p95Ms", 2_000L);
	private static final long LIMITE_ANUAL_MS = Long.getLong("load.annualMs", 5_000L);
	private static final int AQUECIMENTO = 10;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(HOJE.atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final HttpClient http = HttpClient.newHttpClient();
	private final List<UUID> usuarios = new ArrayList<>();
	private User usuario;
	private String token;
	private List<UUID> categoriasDeReceita;

	private record Operacao(String nome, boolean anual, IntFunction<HttpRequest.Builder> requisicao) {
	}

	private record Amostra(String operacao, long micros) {
	}

	@BeforeAll
	void prepararMassa() {
		usuario = userRepository.save(new User("Carga", "carga." + UUID.randomUUID() + "@perf.exemplo.com",
				passwordEncoder.encode(SENHA)));
		usuarios.add(usuario.getId());
		categoryService.createDefaultCategories(usuario.getId());
		categoriasDeReceita = categoryService.list(usuario.getId(), TransactionType.INCOME).stream().map(c -> c.id()).toList();
		List<UUID> despesas = categoryService.list(usuario.getId(), TransactionType.EXPENSE).stream().map(c -> c.id()).toList();

		Random random = new Random(42);
		int dias = (int) (HOJE.toEpochDay() - PRIMEIRO_DIA.toEpochDay()) + 1;
		List<Object[]> linhas = new ArrayList<>(TRANSACOES);
		for (int i = 0; i < TRANSACOES; i++) {
			boolean receita = random.nextInt(100) < 40;
			List<UUID> categorias = receita ? categoriasDeReceita : despesas;
			LocalDate data = PRIMEIRO_DIA.plusDays(random.nextInt(dias));
			Timestamp criado = Timestamp.valueOf(data.atStartOfDay().plusSeconds(i % 86_000));
			linhas.add(new Object[] {
					UUID.randomUUID(), usuario.getId(), categorias.get(random.nextInt(categorias.size())),
					receita ? "INCOME" : "EXPENSE", java.math.BigDecimal.valueOf(1 + random.nextInt(50_000), 2),
					Date.valueOf(data), "Lançamento de carga " + i, random.nextInt(100) < 3, criado, criado });
		}
		jdbcTemplate.batchUpdate("""
				insert into transactions (id, user_id, category_id, type, amount, date, description, deleted,
				                          created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", linhas);
		try {
			jdbcTemplate.execute("ANALYZE");
		} catch (RuntimeException e) {
			// ANALYZE é só uma dica ao planejador; ignorar se o banco não suportar.
		}
		token = login();
	}

	@AfterAll
	void limparMassa() {
		for (UUID id : usuarios) {
			jdbcTemplate.update("delete from login_attempts where email = ?", usuario.getEmail());
			jdbcTemplate.update("delete from das_guides where user_id = ?", id);
			jdbcTemplate.update("delete from transactions where user_id = ?", id);
			jdbcTemplate.update("delete from categories where user_id = ?", id);
			jdbcTemplate.update("delete from users where id = ?", id);
		}
	}

	@Test
	void operacoesCriticasRespeitamOsLimitesDeTempo() throws Exception {
		List<Operacao> operacoes = operacoes();

		// Aquecimento (JIT, pool de conexões, caches do Hibernate): não entra nas medições.
		for (Operacao operacao : operacoes) {
			for (int i = 0; i < AQUECIMENTO; i++) {
				executar(operacao, i);
			}
		}

		List<Amostra> sequencial = new ArrayList<>();
		for (Operacao operacao : operacoes) {
			for (int i = 0; i < REPETICOES; i++) {
				sequencial.add(executar(operacao, i));
			}
		}

		List<Amostra> concorrente = new ArrayList<>();
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			List<Future<List<Amostra>>> futuros = new ArrayList<>();
			for (int t = 0; t < THREADS; t++) {
				int deslocamento = t * 7;
				futuros.add(pool.submit(() -> {
					List<Amostra> locais = new ArrayList<>();
					for (Operacao operacao : operacoes) {
						for (int i = 0; i < Math.max(1, REPETICOES / 2); i++) {
							locais.add(executar(operacao, i + deslocamento));
						}
					}
					return locais;
				}));
			}
			for (Future<List<Amostra>> futuro : futuros) {
				concorrente.addAll(futuro.get());
			}
		} finally {
			pool.shutdownNow();
		}

		String relatorio = relatorio(operacoes, sequencial, concorrente);
		System.out.println(relatorio);
		Path destino = Path.of("target", "load-test-results.md");
		Files.createDirectories(destino.getParent());
		Files.writeString(destino, relatorio, StandardCharsets.UTF_8);

		assertThat(percentil(sequencial, 95) / 1000.0)
				.as("p95 de todas as operações medidas (sequencial), em ms").isLessThan(LIMITE_P95_MS);
		assertThat(percentil(concorrente, 95) / 1000.0)
				.as("p95 de todas as operações medidas (%d usuários simultâneos), em ms", THREADS)
				.isLessThan(LIMITE_P95_MS);
		for (List<Amostra> fase : List.of(sequencial, concorrente)) {
			List<Amostra> anual = fase.stream().filter(a -> a.operacao().startsWith("Relatório anual")).toList();
			assertThat(anual.stream().mapToLong(Amostra::micros).max().orElseThrow() / 1000.0)
					.as("maior tempo do relatório anual, em ms").isLessThan(LIMITE_ANUAL_MS);
		}
	}

	private List<Operacao> operacoes() {
		int meses = 20; // jan/2025 a ago/2026 têm lançamentos em todos os meses
		IntFunction<YearMonth> mes = i -> YearMonth.of(2025, 1).plusMonths(Math.floorMod(i, meses));
		UUID categoria = categoriasDeReceita.get(0);
		return List.of(
				new Operacao("Login", false, i -> HttpRequest.newBuilder(uri("/api/v1/auth/login"))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(
								"{\"email\":\"" + usuario.getEmail() + "\",\"password\":\"" + SENHA + "\"}"))),
				new Operacao("Listagem de lançamentos (página 20, sem filtro)", false,
						i -> autenticada("/api/v1/transactions?page=" + Math.floorMod(i, 50) + "&size=20")),
				new Operacao("Listagem de lançamentos (mês + tipo)", false, i -> autenticada(
						"/api/v1/transactions?page=0&size=20&type=INCOME&from=" + mes.apply(i).atDay(1)
								+ "&to=" + mes.apply(i).atEndOfMonth())),
				new Operacao("Listagem de lançamentos (categoria + descrição)", false, i -> autenticada(
						"/api/v1/transactions?page=0&size=20&categoryId=" + categoria + "&description=carga%20" + (i % 10))),
				new Operacao("Resumo de saldo do mês", false,
						i -> autenticada("/api/v1/transactions/summary?month=" + mes.apply(i))),
				new Operacao("Faturamento anual", false, i -> autenticada("/api/v1/revenue/summary?year=2026")),
				new Operacao("DAS do ano", false, i -> autenticada("/api/v1/das?year=" + (i % 2 == 0 ? 2026 : 2025))),
				new Operacao("Relatório mensal", false, i -> autenticada(
						"/api/v1/reports?period=MONTHLY&year=" + mes.apply(i).getYear() + "&month=" + mes.apply(i).getMonthValue())),
				new Operacao("Relatório anual", true, i -> autenticada(
						"/api/v1/reports?period=ANNUAL&year=" + (i % 2 == 0 ? 2026 : 2025))));
	}

	private HttpRequest.Builder autenticada(String caminho) {
		return HttpRequest.newBuilder(uri(caminho)).header("Authorization", "Bearer " + token).GET();
	}

	private URI uri(String caminho) {
		return URI.create("http://localhost:" + port + caminho);
	}

	private Amostra executar(Operacao operacao, int indice) throws IOException, InterruptedException {
		HttpRequest request = operacao.requisicao().apply(indice).build();
		long inicio = System.nanoTime();
		HttpResponse<String> resposta = http.send(request, HttpResponse.BodyHandlers.ofString());
		long micros = (System.nanoTime() - inicio) / 1_000;
		assertThat(resposta.statusCode()).as("%s -> %s", operacao.nome(), resposta.body()).isEqualTo(200);
		return new Amostra(operacao.nome(), micros);
	}

	private String login() {
		try {
			HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							"{\"email\":\"" + usuario.getEmail() + "\",\"password\":\"" + SENHA + "\"}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			Matcher matcher = TOKEN.matcher(resposta.body());
			assertThat(matcher.find()).as("login: %s", resposta.body()).isTrue();
			return matcher.group(1);
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Percentil por posição (nearest-rank), em microssegundos. */
	private static double percentil(List<Amostra> amostras, int p) {
		long[] ordenado = amostras.stream().mapToLong(Amostra::micros).sorted().toArray();
		int indice = (int) Math.ceil(p / 100.0 * ordenado.length) - 1;
		return ordenado[Math.max(0, indice)];
	}

	private String relatorio(List<Operacao> operacoes, List<Amostra> sequencial, List<Amostra> concorrente) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Resultados do teste de carga\n\n");
		sb.append("Lançamentos do usuário: ").append(TRANSACOES).append(" | repetições por operação: ")
				.append(REPETICOES).append(" | banco: ")
				.append(jdbcTemplate.queryForObject("select 1", Integer.class) == null ? "?" : banco()).append("\n\n");
		tabela(sb, "Sequencial (1 usuário)", operacoes, sequencial);
		tabela(sb, "Concorrente (" + THREADS + " usuários simultâneos)", operacoes, concorrente);
		return sb.toString();
	}

	private String banco() {
		return jdbcTemplate.execute((java.sql.Connection c) ->
				c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
	}

	private void tabela(StringBuilder sb, String titulo, List<Operacao> operacoes, List<Amostra> amostras) {
		sb.append("## ").append(titulo).append("\n\n");
		sb.append("| Operação | Amostras | p50 (ms) | p95 (ms) | Máx (ms) |\n|---|---:|---:|---:|---:|\n");
		for (Operacao operacao : operacoes) {
			List<Amostra> doTipo = amostras.stream().filter(a -> a.operacao().equals(operacao.nome())).toList();
			sb.append(String.format(java.util.Locale.ROOT, "| %s | %d | %.1f | %.1f | %.1f |%n", operacao.nome(),
					doTipo.size(), percentil(doTipo, 50) / 1000.0, percentil(doTipo, 95) / 1000.0,
					doTipo.stream().mapToLong(Amostra::micros).max().orElse(0) / 1000.0));
		}
		long abaixo = amostras.stream().filter(a -> a.micros() < LIMITE_P95_MS * 1000).count();
		sb.append(String.format(java.util.Locale.ROOT,
				"| **Todas** | %d | %.1f | %.1f | %.1f |%n%n", amostras.size(), percentil(amostras, 50) / 1000.0,
				percentil(amostras, 95) / 1000.0,
				amostras.stream().mapToLong(Amostra::micros).max().orElse(0) / 1000.0));
		sb.append(String.format(java.util.Locale.ROOT, "%.1f%% das medições abaixo de %d ms.%n%n",
				100.0 * abaixo / amostras.size(), LIMITE_P95_MS));
	}
}
