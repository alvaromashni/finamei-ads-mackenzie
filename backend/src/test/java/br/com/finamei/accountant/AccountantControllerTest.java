package br.com.finamei.accountant;

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
import br.com.finamei.security.JwtService;
import br.com.finamei.transaction.Transaction;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.Role;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountantControllerTest {

	private static final String CLIENTS = "/api/v1/accountant/clients";

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
	void deveListarSomenteClientesComVinculoAtivo() throws Exception {
		User contador = criar("Ana", "ana.cli@contabil.com", Role.ACCOUNTANT);
		User ativo = criar("Maria Ativa", "maria.ativa@exemplo.com", Role.MEI);
		User revogado = criar("João Revogado", "joao.revogado@exemplo.com", Role.MEI);
		criar("Sem Vínculo", "sem.vinculo@exemplo.com", Role.MEI);
		conceder(ativo, contador);
		AccountantAccessResponse acessoRevogado = conceder(revogado, contador);
		accessService.revoke(revogado.getId(), acessoRevogado.id());

		mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ativo.getId().toString()))
				.andExpect(jsonPath("$[0].name").value("Maria Ativa"))
				.andExpect(jsonPath("$[0].email").value("maria.ativa@exemplo.com"))
				.andExpect(jsonPath("$[0].grantedAt").exists())
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void naoDeveListarClientesDeOutroContador() throws Exception {
		User ana = criar("Ana", "ana.outro@contabil.com", Role.ACCOUNTANT);
		User bia = criar("Bia", "bia.outro@contabil.com", Role.ACCOUNTANT);
		conceder(criar("Maria", "maria.outro@exemplo.com", Role.MEI), ana);

		mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, bearer(bia)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void vinculoAtivoDevePermitirRelatorioDoCliente() throws Exception {
		User contador = criar("Ana", "ana.rel@contabil.com", Role.ACCOUNTANT);
		User mei = criar("Maria", "maria.rel.cliente@exemplo.com", Role.MEI);
		User outro = criar("Outro", "outro.rel.cliente@exemplo.com", Role.MEI);
		conceder(mei, contador);
		lancar(mei, TransactionType.INCOME, "1000.00", LocalDate.of(2026, 3, 1));
		lancar(mei, TransactionType.EXPENSE, "300.00", LocalDate.of(2026, 3, 31));
		lancar(mei, TransactionType.INCOME, "5000.00", LocalDate.of(2026, 4, 1));
		lancar(outro, TransactionType.INCOME, "7000.00", LocalDate.of(2026, 3, 10));

		mockMvc.perform(get(reports(mei)).param("period", "MONTHLY").param("year", "2026").param("month", "3")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.income").value(1000.00))
				.andExpect(jsonPath("$.expense").value(300.00))
				.andExpect(jsonPath("$.balance").value(700.00))
				.andExpect(jsonPath("$.transactionCount").value(2))
				.andExpect(jsonPath("$.revenue.accumulated").value(6000.00));
	}

	@Test
	void revogacaoDeveBloquearORelatorioNaHora() throws Exception {
		User contador = criar("Ana", "ana.revoga.rel@contabil.com", Role.ACCOUNTANT);
		User mei = criar("Maria", "maria.revoga.rel@exemplo.com", Role.MEI);
		AccountantAccessResponse acesso = conceder(mei, contador);
		String auth = bearer(contador);

		mockMvc.perform(get(reports(mei)).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk());

		accessService.revoke(mei.getId(), acesso.id());

		// Mesmo token ainda válido: o vínculo é consultado no banco a cada requisição.
		mockMvc.perform(get(reports(mei)).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void clienteSemVinculoDeveResponder403() throws Exception {
		User contador = criar("Ana", "ana.semvinc@contabil.com", Role.ACCOUNTANT);
		User mei = criar("Maria", "maria.semvinc@exemplo.com", Role.MEI);
		User outroContador = criar("Bia", "bia.semvinc@contabil.com", Role.ACCOUNTANT);
		conceder(mei, outroContador);
		lancar(mei, TransactionType.INCOME, "1.00", LocalDate.of(2026, 3, 1));

		// Vinculado a outro contador não vale para esta contadora.
		mockMvc.perform(get(reports(mei)).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.income").doesNotExist());
	}

	@Test
	void clienteInexistenteDeveResponder403IgualAoSemVinculo() throws Exception {
		User contador = criar("Ana", "ana.inexistente@contabil.com", Role.ACCOUNTANT);

		mockMvc.perform(get("/api/v1/accountant/clients/" + UUID.randomUUID() + "/reports")
						.param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void periodoInvalidoComVinculoAtivoDeveResponder400() throws Exception {
		User contador = criar("Ana", "ana.periodo@contabil.com", Role.ACCOUNTANT);
		User mei = criar("Maria", "maria.periodo@exemplo.com", Role.MEI);
		conceder(mei, contador);

		mockMvc.perform(get(reports(mei)).param("period", "MONTHLY").param("year", "2026").param("month", "13")
						.header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.month").exists());
	}

	@Test
	void meiNaoPodeAcessarAreaDoContador() throws Exception {
		User mei = criar("Maria", "maria.proibida.area@exemplo.com", Role.MEI);
		String auth = bearer(mei);

		mockMvc.perform(get(CLIENTS).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		// Nem os próprios dados pela rota do contador.
		mockMvc.perform(get(reports(mei)).param("period", "ANNUAL").param("year", "2026")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(CLIENTS)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/accountant/clients/" + UUID.randomUUID() + "/reports")
						.param("period", "ANNUAL").param("year", "2026"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void areaDoContadorNaoDeveAceitarEscrita() throws Exception {
		User contador = criar("Ana", "ana.escrita@contabil.com", Role.ACCOUNTANT);
		User mei = criar("Maria", "maria.escrita@exemplo.com", Role.MEI);
		conceder(mei, contador);
		String auth = bearer(contador);

		mockMvc.perform(post(CLIENTS).header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put(reports(mei)).header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch(reports(mei)).header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete(reports(mei)).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isForbidden());
	}

	private static String reports(User cliente) {
		return CLIENTS + "/" + cliente.getId() + "/reports";
	}

	private AccountantAccessResponse conceder(User mei, User contador) {
		return accessService.grant(mei.getId(), contador.getEmail());
	}

	private User criar(String nome, String email, Role role) {
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
