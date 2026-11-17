package br.com.finamei.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.category.Category;
import br.com.finamei.category.CategoryRepository;
import br.com.finamei.category.CategoryService;
import br.com.finamei.security.JwtService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionEditDeleteFilterTest {

    private static final String TRANSACTIONS_PATH = "/api/v1/transactions";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** "Hoje" fixo em 15/09/2026. */
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

    private User maria;
    private UUID vendaDeProdutos;
    private UUID prestacaoDeServicos;
    private UUID aluguel;

    @BeforeEach
    void criarUsuario() {
        maria = userRepository.save(new User("Maria Silva", "maria.edicao@exemplo.com", "hash"));
        categoryService.createDefaultCategories(maria.getId());
        vendaDeProdutos = idDaCategoria(maria, "Venda de produtos");
        prestacaoDeServicos = idDaCategoria(maria, "Prestação de serviços");
        aluguel = idDaCategoria(maria, "Aluguel");
    }

    // ---------- Editar ----------

    @Test
    void deveEditarUmLancamento() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Venda");

        editar(venda.getId(), """
                {"type": "INCOME", "amount": 650.25, "date": "2026-09-11",
                 "categoryId": "%s", "description": " Serviço de buffet "}
                """.formatted(prestacaoDeServicos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(venda.getId().toString()))
                .andExpect(jsonPath("$.amount").value(650.25))
                .andExpect(jsonPath("$.date").value("2026-09-11"))
                .andExpect(jsonPath("$.description").value("Serviço de buffet"))
                .andExpect(jsonPath("$.category.name").value("Prestação de serviços"));

        transactionRepository.flush();
        Transaction lido = transactionRepository.findById(venda.getId()).orElseThrow();
        assertThat(lido.getAmount()).isEqualByComparingTo("650.25");
        assertThat(lido.getCategoryId()).isEqualTo(prestacaoDeServicos);
    }

    @Test
    void deveAplicarAsRegrasDaRn06NaEdicao() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Venda");

        editar(venda.getId(), """
                {"type": "INCOME", "amount": 0, "date": "2026-09-16",
                 "categoryId": "%s", "description": ""}
                """.formatted(vendaDeProdutos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        editar(venda.getId(), corpo(TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-09-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").value("A data não pode ser futura."));
    }

    @Test
    void devePermitirManterACategoriaAtualMesmoInativa() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Venda");
        categoryRepository.findById(vendaDeProdutos).orElseThrow().deactivate();
        categoryRepository.flush();

        editar(venda.getId(), corpo(TransactionType.INCOME, vendaDeProdutos, "700.00", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(700.00));
    }

    @Test
    void naoDevePermitirTrocarParaOutraCategoriaInativa() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Venda");
        Category antiga = categoryRepository.save(new Category(maria.getId(), "Vendas antigas", TransactionType.INCOME));
        antiga.deactivate();
        categoryRepository.flush();

        editar(venda.getId(), corpo(TransactionType.INCOME, antiga.getId(), "500.00", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId")
                        .value("A categoria selecionada está inativa. Escolha outra categoria."));
    }

    @Test
    void deveRecusarCategoriaDeOutroTipoNaEdicao() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Venda");

        editar(venda.getId(), corpo(TransactionType.INCOME, aluguel, "500.00", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId")
                        .value("A categoria selecionada não corresponde ao tipo do lançamento."));
    }

    @Test
    void deveRecalcularOFaturamentoDepoisDeEditar() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "1000.00", "2026-09-10", "Venda");

        editar(venda.getId(), corpo(TransactionType.EXPENSE, aluguel, "1000.00", "2026-09-10"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/revenue/summary").param("year", "2026").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$.accumulated").value(0));
    }

    @Test
    void naoDeveEditarLancamentoDeOutroUsuarioNemExcluido() throws Exception {
        User joao = userRepository.save(new User("João Souza", "joao.edicao@exemplo.com", "hash"));
        Transaction doJoao = lancar(joao, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Do João");
        Transaction excluido = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-10", "Excluído");
        excluido.markAsDeleted();
        transactionRepository.flush();

        editar(doJoao.getId(), corpo(TransactionType.INCOME, vendaDeProdutos, "1.00", "2026-09-10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Você não tem permissão para acessar este recurso."));
        editar(excluido.getId(), corpo(TransactionType.INCOME, vendaDeProdutos, "1.00", "2026-09-10"))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(doJoao.getId()).orElseThrow().getAmount()).isEqualByComparingTo("500.00");
    }

    // ---------- Excluir ----------

    @Test
    void deveExcluirLogicamenteERecalcularSaldoEFaturamento() throws Exception {
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "1000.00", "2026-09-10", "Venda");
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "300.00", "2026-09-11", "Outra venda");

        excluir(maria, venda.getId()).andExpect(status().isNoContent());

        transactionRepository.flush();
        assertThat(transactionRepository.findById(venda.getId()).orElseThrow().isDeleted()).isTrue();
        mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].description").value("Outra venda"));
        mockMvc.perform(get(TRANSACTIONS_PATH + "/summary").param("month", "2026-09").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$.balance").value(300.00));
        mockMvc.perform(get("/api/v1/revenue/summary").param("year", "2026").header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$.accumulated").value(300.00));
    }

    @Test
    void deveResponder403AoExcluirDeOutroUsuarioE404AoExcluirDeNovo() throws Exception {
        User joao = userRepository.save(new User("João Souza", "joao.exclusao@exemplo.com", "hash"));
        Transaction venda = lancar(maria, TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-09-10", "Venda");

        excluir(joao, venda.getId()).andExpect(status().isForbidden());
        excluir(maria, venda.getId()).andExpect(status().isNoContent());
        excluir(maria, venda.getId()).andExpect(status().isNotFound());
    }

    // ---------- Filtrar ----------

    @Test
    void deveFiltrarPorPeriodoTipoCategoriaEDescricao() throws Exception {
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-08-30", "Bolo de aniversário");
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "200.00", "2026-09-05", "Bolo de casamento");
        lancar(maria, TransactionType.INCOME, prestacaoDeServicos, "300.00", "2026-09-06", "Buffet de BOLO");
        lancar(maria, TransactionType.EXPENSE, aluguel, "400.00", "2026-09-07", "Aluguel da cozinha");
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "500.00", "2026-09-12", "Doces");

        listar(get(TRANSACTIONS_PATH).param("from", "2026-09-01").param("to", "2026-09-10"))
                .andExpect(jsonPath("$.content[*].description",
                        contains("Aluguel da cozinha", "Buffet de BOLO", "Bolo de casamento")));

        listar(get(TRANSACTIONS_PATH).param("type", "EXPENSE"))
                .andExpect(jsonPath("$.content[*].description", contains("Aluguel da cozinha")));

        listar(get(TRANSACTIONS_PATH).param("categoryId", prestacaoDeServicos.toString()))
                .andExpect(jsonPath("$.content[*].description", contains("Buffet de BOLO")));

        listar(get(TRANSACTIONS_PATH).param("description", "  bolo "))
                .andExpect(jsonPath("$.totalElements").value(3));

        listar(get(TRANSACTIONS_PATH).param("description", "bolo").param("type", "INCOME")
                        .param("categoryId", vendaDeProdutos.toString()).param("from", "2026-09-01"))
                .andExpect(jsonPath("$.content[*].description", contains("Bolo de casamento")));
    }

    @Test
    void deveProcurarPorcentagemESublinhadoLiteralmenteNaDescricao() throws Exception {
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-09-05", "Desconto de 10% à vista");
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-09-06", "Venda comum");

        listar(get(TRANSACTIONS_PATH).param("description", "%"))
                .andExpect(jsonPath("$.content[*].description", contains("Desconto de 10% à vista")));
    }

    @Test
    void deveInformarListaVaziaQuandoNadaCombinaComOsFiltros() throws Exception {
        lancar(maria, TransactionType.INCOME, vendaDeProdutos, "100.00", "2026-09-05", "Venda");

        listar(get(TRANSACTIONS_PATH).param("description", "inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deveRecusarDataFinalAnteriorAInicial() throws Exception {
        listar(get(TRANSACTIONS_PATH).param("from", "2026-09-10").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.to").value("A data final deve ser igual ou posterior à data inicial."));
    }

    @Test
    void deveRecusarFiltroComFormatoInvalido() throws Exception {
        listar(get(TRANSACTIONS_PATH).param("from", "10/09/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.from").exists());
    }

    private ResultActions editar(UUID id, String corpo) throws Exception {
        return mockMvc.perform(put(TRANSACTIONS_PATH + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(maria))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions excluir(User usuario, UUID id) throws Exception {
        return mockMvc.perform(delete(TRANSACTIONS_PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(usuario)));
    }

    private ResultActions listar(MockHttpServletRequestBuilder requisicao) throws Exception {
        transactionRepository.flush();
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, bearer(maria)));
    }

    private String corpo(TransactionType tipo, UUID categoria, String valor, String data) {
        return """
                {"type": "%s", "amount": %s, "date": "%s", "categoryId": "%s", "description": "Lançamento"}
                """.formatted(tipo, valor, data, categoria);
    }

    private Transaction lancar(User usuario, TransactionType tipo, UUID categoria, String valor, String data, String descricao) {
        return transactionRepository.save(new Transaction(
                usuario.getId(), categoria, tipo, new BigDecimal(valor), LocalDate.parse(data), descricao));
    }

    private UUID idDaCategoria(User usuario, String nome) {
        return categoryRepository.findByUserIdOrderByTypeAscNameAsc(usuario.getId()).stream()
                .filter(categoria -> categoria.getName().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    private String bearer(User usuario) {
        return "Bearer " + jwtService.generateToken(usuario);
    }
}
