package br.com.finamei.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.accountantaccess.AccountantAccessService;
import br.com.finamei.security.JwtService;
import br.com.finamei.transaction.Transaction;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportExportControllerTest {

	private static final String PATH = "/api/v1/reports/export";
	private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private AccountantAccessService accessService;

	@Autowired
	private ReportService reportService;

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
	void pdfMensalDeveAbrirEConterIdentificacaoPeriodoETotaisDaApiDeRelatorio() throws Exception {
		User maria = usuario("Maria Export", "maria.exp.pdf@exemplo.com", Role.MEI);
		popular(maria);

		MvcResult result = mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026")
						.param("month", "9").param("format", "PDF")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
		assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
				.startsWith("attachment").contains("relatorio-2026-09.pdf");

		PdfReader reader = new PdfReader(result.getResponse().getContentAsByteArray());
		assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
		String text = normalizar(new PdfTextExtractor(reader).getTextFromPage(1));
		var report = reportService.generate(maria.getId(), ReportPeriod.MONTHLY, 2026, 9);
		assertThat(text)
				.contains("Maria Export")
				.contains("Setembro/2026")
				.contains(dinheiro(report.income()))
				.contains(dinheiro(report.expense()))
				.contains(dinheiro(report.balance()))
				.contains(dinheiro(report.revenue().accumulated()))
				.contains(dinheiro(report.revenue().limit()));
		assertThat(report.income()).isEqualByComparingTo("1500.50");
	}

	@Test
	void planilhaMensalDeveListarSomenteLancamentosAtivosDoPeriodoEConferirOsConsolidados() throws Exception {
		User maria = usuario("Maria Export", "maria.exp.xlsx@exemplo.com", Role.MEI);
		popular(maria);

		MvcResult result = mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026")
						.param("month", "9").param("format", "XLSX")
						.header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentType()).isEqualTo(XLSX_TYPE);
		assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
				.startsWith("attachment").contains("relatorio-2026-09.xlsx");

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
			Sheet lancamentos = workbook.getSheet(ReportFileWriters.TRANSACTIONS_SHEET);
			assertThat(lancamentos.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
			assertThat(lancamentos.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Valor");
			// 3 lançamentos ativos em setembro (a excluída, o mês vizinho e o outro usuário ficam de fora).
			assertThat(lancamentos.getLastRowNum()).isEqualTo(3);
			assertThat(lancamentos.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
					.isEqualTo(LocalDate.of(2026, 9, 1));
			assertThat(lancamentos.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Receita");
			assertThat(lancamentos.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Vendas");
			assertThat(lancamentos.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Lançamento de teste");
			assertThat(lancamentos.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(1000.00);

			double soma = 0;
			for (int i = 1; i <= lancamentos.getLastRowNum(); i++) {
				double valor = lancamentos.getRow(i).getCell(4).getNumericCellValue();
				soma += "Receita".equals(lancamentos.getRow(i).getCell(1).getStringCellValue()) ? valor : -valor;
			}
			var report = reportService.generate(maria.getId(), ReportPeriod.MONTHLY, 2026, 9);
			assertThat(soma).isEqualTo(report.balance().doubleValue());

			Sheet resumo = workbook.getSheet(ReportFileWriters.SUMMARY_SHEET);
			assertThat(valorDe(resumo, "Receitas")).isEqualTo(report.income().doubleValue());
			assertThat(valorDe(resumo, "Despesas")).isEqualTo(report.expense().doubleValue());
			assertThat(valorDe(resumo, "Saldo")).isEqualTo(report.balance().doubleValue());
			assertThat(valorDe(resumo, "Quantidade de lançamentos")).isEqualTo(report.transactionCount());
			assertThat(valorDe(resumo, "Faturamento acumulado em 2026"))
					.isEqualTo(report.revenue().accumulated().doubleValue());
			assertThat(valorDe(resumo, "Limite anual do MEI")).isEqualTo(report.revenue().limit().doubleValue());
		}
	}

	@Test
	void relatorioAnualVazioDeveGerarPdfEPlanilhaValidos() throws Exception {
		User maria = usuario("Maria Vazia", "maria.exp.vazio@exemplo.com", Role.MEI);

		MvcResult pdf = mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026")
						.param("format", "PDF").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(pdf.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("relatorio-2026.pdf");
		PdfReader reader = new PdfReader(pdf.getResponse().getContentAsByteArray());
		assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Ano 2026").contains("Nenhum lançamento");

		MvcResult xlsx = mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026")
						.param("format", "XLSX").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isOk())
				.andReturn();
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
			assertThat(workbook.getSheet(ReportFileWriters.TRANSACTIONS_SHEET).getLastRowNum()).isZero();
		}
	}

	@Test
	void deveRejeitarFormatoInvalidoOuAusenteEPeriodoInvalidoCom400() throws Exception {
		User maria = usuario("Maria Erro", "maria.exp.erro@exemplo.com", Role.MEI);
		String auth = bearer(maria);

		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026").param("format", "DOCX")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.format").exists());
		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.format").exists());
		mockMvc.perform(get(PATH).param("period", "MONTHLY").param("year", "2026").param("month", "13")
						.param("format", "PDF").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.month").exists());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(PATH).param("period", "ANNUAL").param("year", "2026").param("format", "PDF"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void contadorComVinculoExportaORelatorioDoClienteEnaoDeOutroMei() throws Exception {
		User contador = usuario("Ana Contadora", "ana.exp@contabil.com", Role.ACCOUNTANT);
		User cliente = usuario("Cliente Vinculado", "cliente.exp@exemplo.com", Role.MEI);
		User outro = usuario("Outro Mei", "outro.exp@exemplo.com", Role.MEI);
		accessService.grant(cliente.getId(), contador.getEmail());
		lancar(cliente, TransactionType.INCOME, "321.00", LocalDate.of(2026, 9, 5));
		lancar(outro, TransactionType.INCOME, "9999.00", LocalDate.of(2026, 9, 5));

		MvcResult result = mockMvc.perform(get(exportDoContador(cliente)).param("period", "MONTHLY")
						.param("year", "2026").param("month", "9").param("format", "XLSX")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentType()).isEqualTo(XLSX_TYPE);
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
			Sheet lancamentos = workbook.getSheet(ReportFileWriters.TRANSACTIONS_SHEET);
			assertThat(lancamentos.getLastRowNum()).isEqualTo(1);
			assertThat(lancamentos.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(321.00);
			assertThat(workbook.getSheet(ReportFileWriters.SUMMARY_SHEET).getRow(1).getCell(1).getStringCellValue())
					.isEqualTo("Cliente Vinculado");
		}

		mockMvc.perform(get(exportDoContador(outro)).param("period", "MONTHLY").param("year", "2026")
						.param("month", "9").param("format", "PDF")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void contadorComVinculoRevogadoEMeiNaoExportamPelaRotaDoContador() throws Exception {
		User contador = usuario("Ana Contadora", "ana.exp.rev@contabil.com", Role.ACCOUNTANT);
		User cliente = usuario("Cliente Revogado", "cliente.exp.rev@exemplo.com", Role.MEI);
		var acesso = accessService.grant(cliente.getId(), contador.getEmail());
		accessService.revoke(cliente.getId(), acesso.id());

		mockMvc.perform(get(exportDoContador(cliente)).param("period", "ANNUAL").param("year", "2026")
						.param("format", "PDF").header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(exportDoContador(cliente)).param("period", "ANNUAL").param("year", "2026")
						.param("format", "PDF").header(HttpHeaders.AUTHORIZATION, bearer(cliente)))
				.andExpect(status().isForbidden());
	}

	private static String dinheiro(java.math.BigDecimal valor) {
		return normalizar(ReportFileWriters.money(valor));
	}

	private static String normalizar(String texto) {
		return texto.replaceAll("[\u00a0\u202f\s]+", " ");
	}

	private static String exportDoContador(User cliente) {
		return "/api/v1/accountant/clients/" + cliente.getId() + "/reports/export";
	}

	private static double valorDe(Sheet resumo, String rotulo) {
		for (int i = 0; i <= resumo.getLastRowNum(); i++) {
			var row = resumo.getRow(i);
			if (row != null && row.getCell(0) != null && rotulo.equals(row.getCell(0).getStringCellValue())
					&& row.getCell(1) != null) {
				return row.getCell(1).getNumericCellValue();
			}
		}
		throw new AssertionError("Rótulo não encontrado na aba de resumo: " + rotulo);
	}

	private void popular(User maria) {
		User joao = usuario("João", "joao." + maria.getEmail(), Role.MEI);
		lancar(maria, TransactionType.INCOME, "1000.00", LocalDate.of(2026, 9, 1));
		lancar(maria, TransactionType.INCOME, "500.50", LocalDate.of(2026, 9, 30));
		lancar(maria, TransactionType.EXPENSE, "200.25", LocalDate.of(2026, 9, 15));
		lancar(maria, TransactionType.INCOME, "9999.00", LocalDate.of(2026, 8, 31));
		lancar(maria, TransactionType.EXPENSE, "8888.00", LocalDate.of(2026, 10, 1));
		lancar(maria, TransactionType.INCOME, "7777.00", LocalDate.of(2026, 9, 10)).markAsDeleted();
		lancar(joao, TransactionType.INCOME, "5555.00", LocalDate.of(2026, 9, 10));
		transactionRepository.flush();
	}

	private User usuario(String nome, String email, Role role) {
		User usuario = new User(nome, email, "hash");
		ReflectionTestUtils.setField(usuario, "role", role);
		return userRepository.save(usuario);
	}

	private Transaction lancar(User usuario, TransactionType tipo, String valor, LocalDate data) {
		return transactionRepository.save(
				new Transaction(usuario.getId(), categoriaId, tipo, new BigDecimal(valor), data, "Lançamento de teste"));
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
