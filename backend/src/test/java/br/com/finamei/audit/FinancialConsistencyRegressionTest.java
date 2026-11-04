package br.com.finamei.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.category.CategoryRepository;
import br.com.finamei.category.CategoryService;
import br.com.finamei.security.JwtService;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regressão ponta a ponta (MockMvc) de ONF10 e RN08: depois de criar, editar e excluir
 * logicamente lançamentos, o saldo ({@code /transactions/summary}), o faturamento
 * ({@code /revenue/summary}) e os relatórios ({@code /reports}) refletem só os lançamentos ativos,
 * e a auditoria registra usuário, instante e operação de cada escrita bem-sucedida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialConsistencyRegressionTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final Instant AGORA = LocalDate.of(2026, 9, 15).atTime(10, 0).atZone(FUSO).toInstant();

	/** "Hoje" fixo em 15/09/2026: o instante da auditoria é previsível. */
	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(AGORA, FUSO);
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
	private AuditLogRepository auditLogRepository;

	private User maria;
	private User joao;
	private String receita;
	private String despesa;

	@BeforeEach
	void prepararUsuarios() {
		maria = criarUsuario("maria.regressao@exemplo.com");
		joao = criarUsuario("joao.regressao@exemplo.com");
		receita = idDaCategoria(maria, "Venda de produtos");
		despesa = idDaCategoria(maria, "Aluguel");
	}

	// ---------- Auditoria: usuário, instante e operação ----------

	@Test
	void auditoriaIdentificaUsuarioInstanteEOperacaoDeCadaEscritaBemSucedida() throws Exception {
		String id = criar(maria, "INCOME", "1000.00", "2026-09-03", receita);
		editar(maria, id, "INCOME", "1500.00", "2026-09-03", receita).andExpect(status().isOk());
		excluir(maria, id).andExpect(status().isNoContent());

		List<AuditLog> registros = auditLogRepository.findByResourceTypeAndResourceId(
				AuditResourceType.TRANSACTION, UUID.fromString(id));
		assertThat(registros).extracting(AuditLog::getAction)
				.containsExactlyInAnyOrder(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
		assertThat(registros).allSatisfy(registro -> {
			assertThat(registro.getActorUserId()).isEqualTo(maria.getId());
			assertThat(registro.getOccurredAt()).isEqualTo(AGORA);
			assertThat(registro.getResourceType()).isEqualTo(AuditResourceType.TRANSACTION);
		});
		AuditLog edicao = registros.stream().filter(r -> r.getAction() == AuditAction.UPDATE).findFirst().orElseThrow();
		assertThat(edicao.getDetails()).contains("amount=1000.00").contains("amount=1500.00");
		AuditLog exclusao = registros.stream().filter(r -> r.getAction() == AuditAction.DELETE).findFirst().orElseThrow();
		assertThat(exclusao.getDetails()).contains("amount=1500.00");
	}

	@Test
	void auditoriaNaoMisturaUsuariosEEscritasRecusadasNaoGeramRegistro() throws Exception {
		String lancamentoDaMaria = criar(maria, "INCOME", "100.00", "2026-09-03", receita);
		long antesDoJoao = auditLogRepository.countByActorUserId(joao.getId());

		// Tentativas inválidas ou de outro usuário: nenhuma pode deixar rastro de escrita.
		editar(joao, lancamentoDaMaria, "INCOME", "1.00", "2026-09-03", receita).andExpect(status().isNotFound());
		excluir(joao, lancamentoDaMaria).andExpect(status().isNotFound());
		criarResposta(maria, "INCOME", "-5", "2026-09-03", receita).andExpect(status().isBadRequest());
		criarResposta(maria, "INCOME", "5", "2026-12-25", receita).andExpect(status().isBadRequest());
		excluir(maria, UUID.randomUUID().toString()).andExpect(status().isNotFound());

		assertThat(auditLogRepository.countByActorUserId(joao.getId())).isEqualTo(antesDoJoao).isZero();
		assertThat(auditLogRepository.countByActorUserId(maria.getId())).isEqualTo(1);

		excluir(maria, lancamentoDaMaria).andExpect(status().isNoContent());
		// Excluir de novo e editar um excluído respondem 404 e não geram novo registro.
		excluir(maria, lancamentoDaMaria).andExpect(status().isNotFound());
		editar(maria, lancamentoDaMaria, "INCOME", "1.00", "2026-09-03", receita).andExpect(status().isNotFound());
		assertThat(auditLogRepository.countByActorUserId(maria.getId())).isEqualTo(2);
	}

	// ---------- Criação, edição e exclusão: antes e depois ----------

	@Test
	void criacaoAtualizaSaldoFaturamentoERelatoriosEExclusaoDesfazTudo() throws Exception {
		Totais vazio = totais(maria, 2026, 9);
		assertThat(vazio).isEqualTo(new Totais("0", "0", "0", "0", "0", "0", 0));

		String receitaId = criar(maria, "INCOME", "1000.00", "2026-09-03", receita);
		String despesaId = criar(maria, "EXPENSE", "400.00", "2026-09-04", despesa);

		// Saldo 600; mês: receita 1000, despesa 400; faturamento só conta receita.
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("600", "1000", "400", "1000", "1000", "400", 2));

		excluir(maria, despesaId).andExpect(status().isNoContent());
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("1000", "1000", "0", "1000", "1000", "0", 1));

		excluir(maria, receitaId).andExpect(status().isNoContent());
		assertThat(totais(maria, 2026, 9)).isEqualTo(vazio);

		mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(jsonPath("$.content", hasSize(0)));
	}

	@Test
	void edicaoDeValorRecalculaTodosOsTotais() throws Exception {
		String id = criar(maria, "INCOME", "1000.00", "2026-09-03", receita);
		assertThat(totais(maria, 2026, 9).saldo()).isEqualByComparingTo("1000");

		editar(maria, id, "INCOME", "1750.50", "2026-09-03", receita).andExpect(status().isOk());

		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("1750.50", "1750.50", "0", "1750.50", "1750.50", "0", 1));
	}

	@Test
	void edicaoDeTipoMoveOValorDeReceitaParaDespesaEReduzOFaturamento() throws Exception {
		String id = criar(maria, "INCOME", "1000.00", "2026-09-03", receita);
		criar(maria, "INCOME", "200.00", "2026-09-05", receita);
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("1200", "1200", "0", "1200", "1200", "0", 2));

		// A categoria precisa acompanhar o tipo (RN06); o valor deixa de ser receita e vira despesa.
		editar(maria, id, "EXPENSE", "1000.00", "2026-09-03", despesa).andExpect(status().isOk());

		// Saldo: 200 - 1000 = -800; faturamento só os 200 que restam como receita.
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("-800", "200", "1000", "200", "200", "1000", 2));

		editar(maria, id, "INCOME", "1000.00", "2026-09-03", receita).andExpect(status().isOk());
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("1200", "1200", "0", "1200", "1200", "0", 2));
	}

	@Test
	void edicaoDeDataMoveOValorEntreMesesEAnosSemAlterarOSaldoGeral() throws Exception {
		String id = criar(maria, "INCOME", "1000.00", "2026-09-03", receita);
		assertThat(totais(maria, 2026, 9).receitaDoMes()).isEqualByComparingTo("1000");

		// Para agosto do mesmo ano: o mês de setembro esvazia, agosto recebe; faturamento do ano e saldo não mudam.
		editar(maria, id, "INCOME", "1000.00", "2026-08-31", receita).andExpect(status().isOk());
		assertThat(totais(maria, 2026, 9)).isEqualTo(new Totais("1000", "0", "0", "1000", "0", "0", 0));
		assertThat(totais(maria, 2026, 8)).isEqualTo(new Totais("1000", "1000", "0", "1000", "1000", "0", 1));

		// Para o ano anterior: o faturamento de 2026 zera e o de 2025 passa a ter o valor.
		editar(maria, id, "INCOME", "1000.00", "2025-12-31", receita).andExpect(status().isOk());
		assertThat(faturamento(maria, 2026)).isEqualByComparingTo("0");
		assertThat(faturamento(maria, 2025)).isEqualByComparingTo("1000");
		assertThat(totais(maria, 2026, 9).saldo()).isEqualByComparingTo("1000");
		assertThat(new BigDecimal(relatorioAnual(maria, 2026).get("income").toString())).isEqualByComparingTo("0");
		assertThat(new BigDecimal(relatorioAnual(maria, 2025).get("income").toString())).isEqualByComparingTo("1000");
	}

	// ---------- Exclusão em outro mês/ano não afeta o período errado ----------

	@Test
	void exclusaoDeOutroMesOuAnoSoAfetaOPeriodoDoLancamentoExcluido() throws Exception {
		String agosto = criar(maria, "INCOME", "300.00", "2026-08-31", receita);
		criar(maria, "INCOME", "500.00", "2026-09-01", receita);
		String dezembroAnterior = criar(maria, "INCOME", "700.00", "2025-12-31", receita);
		criar(maria, "EXPENSE", "50.00", "2026-09-10", despesa);

		Totais setembroAntes = totais(maria, 2026, 9);
		assertThat(setembroAntes).isEqualTo(new Totais("1450", "500", "50", "800", "500", "50", 2));
		assertThat(faturamento(maria, 2025)).isEqualByComparingTo("700");

		// Excluir o de 31/08 não mexe em setembro (mês) nem em 2025; só no faturamento de 2026 e no saldo geral.
		excluir(maria, agosto).andExpect(status().isNoContent());
		Totais setembroDepois = totais(maria, 2026, 9);
		assertThat(setembroDepois.receitaDoMes()).isEqualByComparingTo(setembroAntes.receitaDoMes());
		assertThat(setembroDepois.despesaDoMes()).isEqualByComparingTo(setembroAntes.despesaDoMes());
		assertThat(setembroDepois.relatorioReceita()).isEqualByComparingTo("500");
		assertThat(setembroDepois.saldo()).isEqualByComparingTo("1150");
		assertThat(setembroDepois.faturamento()).isEqualByComparingTo("500");
		assertThat(totais(maria, 2026, 8).receitaDoMes()).isEqualByComparingTo("0");
		assertThat(faturamento(maria, 2025)).isEqualByComparingTo("700");

		// Excluir o de 31/12/2025 não mexe em nada de 2026, só no saldo geral e em 2025.
		excluir(maria, dezembroAnterior).andExpect(status().isNoContent());
		assertThat(faturamento(maria, 2025)).isEqualByComparingTo("0");
		Totais setembroFinal = totais(maria, 2026, 9);
		assertThat(setembroFinal.faturamento()).isEqualByComparingTo("500");
		assertThat(setembroFinal.receitaDoMes()).isEqualByComparingTo("500");
		assertThat(setembroFinal.saldo()).isEqualByComparingTo("450");
	}

	@Test
	void fronteirasDeMesEAnoNaoVazamParaOPeriodoVizinho() throws Exception {
		criar(maria, "INCOME", "10.00", "2026-01-01", receita);
		criar(maria, "INCOME", "20.00", "2026-02-28", receita);
		String marco = criar(maria, "INCOME", "40.00", "2026-03-01", receita);
		criar(maria, "INCOME", "80.00", "2025-12-31", receita);

		assertThat(totais(maria, 2026, 2).receitaDoMes()).isEqualByComparingTo("20");
		assertThat(totais(maria, 2026, 3).receitaDoMes()).isEqualByComparingTo("40");
		assertThat(faturamento(maria, 2026)).isEqualByComparingTo("70");
		assertThat(faturamento(maria, 2025)).isEqualByComparingTo("80");

		excluir(maria, marco).andExpect(status().isNoContent());
		assertThat(totais(maria, 2026, 2).receitaDoMes()).isEqualByComparingTo("20");
		assertThat(totais(maria, 2026, 3).receitaDoMes()).isEqualByComparingTo("0");
		assertThat(faturamento(maria, 2026)).isEqualByComparingTo("30");
	}

	@Test
	void operacoesDeUmUsuarioNaoAlteramOsTotaisDeOutro() throws Exception {
		criar(joao, "INCOME", "999.00", "2026-09-03", idDaCategoria(joao, "Venda de produtos"));
		Totais joaoAntes = totais(joao, 2026, 9);

		String id = criar(maria, "INCOME", "100.00", "2026-09-03", receita);
		editar(maria, id, "INCOME", "200.00", "2026-09-03", receita).andExpect(status().isOk());
		excluir(maria, id).andExpect(status().isNoContent());

		assertThat(totais(joao, 2026, 9)).isEqualTo(joaoAntes);
	}

	// ---------- Auxiliares ----------

	/** Instantâneo do que a API mostra para um mês/ano. Valores comparados sem considerar escala. */
	private Totais totais(User usuario, int ano, int mes) throws Exception {
		String resumo = mockMvc.perform(get("/api/v1/transactions/summary")
						.param("month", "%d-%02d".formatted(ano, mes))
						.header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		BigDecimal faturamento = faturamento(usuario, ano);
		String relatorio = mockMvc.perform(get("/api/v1/reports")
						.param("period", "MONTHLY").param("year", String.valueOf(ano)).param("month", String.valueOf(mes))
						.header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Totais(
				decimal(resumo, "$.balance"), decimal(resumo, "$.income"), decimal(resumo, "$.expense"),
				faturamento,
				decimal(relatorio, "$.income"), decimal(relatorio, "$.expense"),
				JsonPath.<Number>read(relatorio, "$.transactionCount").longValue());
	}

	private BigDecimal faturamento(User usuario, int ano) throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/revenue/summary").param("year", String.valueOf(ano))
						.header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return decimal(corpo, "$.accumulated");
	}

	private java.util.Map<String, Object> relatorioAnual(User usuario, int ano) throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/reports")
						.param("period", "ANNUAL").param("year", String.valueOf(ano))
						.header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$");
	}

	private static BigDecimal decimal(String json, String caminho) {
		return new BigDecimal(JsonPath.read(json, caminho).toString());
	}

	/**
	 * @param saldo saldo atual (todas as receitas menos todas as despesas ativas)
	 * @param receitaDoMes receita do mês em {@code /transactions/summary}
	 * @param despesaDoMes despesa do mês em {@code /transactions/summary}
	 * @param faturamento acumulado do ano em {@code /revenue/summary}
	 * @param relatorioReceita receita do mês no relatório mensal
	 * @param relatorioDespesa despesa do mês no relatório mensal
	 * @param quantidade lançamentos ativos do mês no relatório
	 */
	private record Totais(BigDecimal saldo, BigDecimal receitaDoMes, BigDecimal despesaDoMes,
			BigDecimal faturamento, BigDecimal relatorioReceita, BigDecimal relatorioDespesa, long quantidade) {

		Totais(String saldo, String receitaDoMes, String despesaDoMes, String faturamento,
				String relatorioReceita, String relatorioDespesa, long quantidade) {
			this(new BigDecimal(saldo), new BigDecimal(receitaDoMes), new BigDecimal(despesaDoMes),
					new BigDecimal(faturamento), new BigDecimal(relatorioReceita), new BigDecimal(relatorioDespesa),
					quantidade);
		}

		@Override
		public boolean equals(Object outro) {
			return outro instanceof Totais t
					&& saldo.compareTo(t.saldo) == 0
					&& receitaDoMes.compareTo(t.receitaDoMes) == 0
					&& despesaDoMes.compareTo(t.despesaDoMes) == 0
					&& faturamento.compareTo(t.faturamento) == 0
					&& relatorioReceita.compareTo(t.relatorioReceita) == 0
					&& relatorioDespesa.compareTo(t.relatorioDespesa) == 0
					&& quantidade == t.quantidade;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(quantidade);
		}
	}

	private String criar(User usuario, String tipo, String valor, String data, String categoria) throws Exception {
		String corpo = criarResposta(usuario, tipo, valor, data, categoria)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$.id");
	}

	private ResultActions criarResposta(User usuario, String tipo, String valor, String data, String categoria)
			throws Exception {
		return mockMvc.perform(post("/api/v1/transactions")
				.header(HttpHeaders.AUTHORIZATION, bearer(usuario))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(tipo, valor, data, categoria)));
	}

	private ResultActions editar(User usuario, String id, String tipo, String valor, String data, String categoria)
			throws Exception {
		return mockMvc.perform(put("/api/v1/transactions/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer(usuario))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(tipo, valor, data, categoria)));
	}

	private ResultActions excluir(User usuario, String id) throws Exception {
		return mockMvc.perform(delete("/api/v1/transactions/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer(usuario)));
	}

	private static String json(String tipo, String valor, String data, String categoria) {
		return """
				{"type": "%s", "amount": %s, "date": "%s", "categoryId": "%s", "description": "Lançamento de regressão"}
				""".formatted(tipo, valor, data, categoria);
	}

	private User criarUsuario(String email) {
		User usuario = userRepository.save(new User("Usuário de teste", email, "hash"));
		categoryService.createDefaultCategories(usuario.getId());
		return usuario;
	}

	private String idDaCategoria(User usuario, String nome) {
		return categoryRepository.findByUserIdOrderByTypeAscNameAsc(usuario.getId()).stream()
				.filter(categoria -> categoria.getName().equals(nome))
				.findFirst().orElseThrow().getId().toString();
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
