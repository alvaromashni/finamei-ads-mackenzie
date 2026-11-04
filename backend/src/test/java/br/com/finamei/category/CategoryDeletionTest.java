package br.com.finamei.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** RN07: categoria com lançamentos vinculados não é excluída, só inativada; o histórico é preservado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryDeletionTest {

    private static final String CATEGORIES_PATH = "/api/v1/categories";
    private static final String TRANSACTIONS_PATH = "/api/v1/transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionRepository transactionRepository;

    private User maria;

    @BeforeEach
    void criarUsuarioComCategoriasPadrao() {
        maria = criarUsuario("maria.exclusao@exemplo.com");
    }

    @Test
    void deveExcluirCategoriaSemLancamentos() throws Exception {
        UUID sorveteria = categoryService.create(maria.getId(), "Sorveteria", TransactionType.INCOME).id();

        excluir(maria, sorveteria).andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(sorveteria)).isEmpty();
        mockMvc.perform(get(CATEGORIES_PATH).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(jsonPath("$[?(@.name == 'Sorveteria')]", hasSize(0)));
    }

    @Test
    void naoDeveExcluirCategoriaComLancamentosVinculados() throws Exception {
        UUID aluguel = idDaCategoria(maria, "Aluguel");
        lancar(maria, aluguel, "1500.00", "2026-09-10", "Aluguel de setembro");

        excluir(maria, aluguel)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.message").value(
                        "Esta categoria tem lançamentos vinculados e não pode ser excluída. "
                                + "Inative-a para que ela deixe de aparecer em novos lançamentos."));

        assertThat(categoryRepository.findById(aluguel)).isPresent();
    }

    @Test
    void naoDeveExcluirCategoriaCujosLancamentosForamExcluidosLogicamente() throws Exception {
        UUID aluguel = idDaCategoria(maria, "Aluguel");
        Transaction lancamento = lancar(maria, aluguel, "1500.00", "2026-09-10", "Aluguel de setembro");
        lancamento.markAsDeleted();
        transactionRepository.save(lancamento);

        excluir(maria, aluguel)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        assertThat(categoryRepository.findById(aluguel)).isPresent();
    }

    @Test
    void inativarCategoriaComLancamentosDevePreservarOHistorico() throws Exception {
        UUID aluguel = idDaCategoria(maria, "Aluguel");
        lancar(maria, aluguel, "1500.00", "2026-09-10", "Aluguel de setembro");

        mockMvc.perform(patch(CATEGORIES_PATH + "/" + aluguel)
                        .header(HttpHeaders.AUTHORIZATION, bearer(maria))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(TRANSACTIONS_PATH).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].description").value("Aluguel de setembro"))
                .andExpect(jsonPath("$.content[0].category.name").value("Aluguel"));
        assertThat(categoryRepository.findById(aluguel).orElseThrow().isActive()).isFalse();
    }

    @Test
    void naoDeveExcluirCategoriaDeOutroUsuario() throws Exception {
        User joao = criarUsuario("joao.exclusao@exemplo.com");
        UUID sorveteriaDaMaria = categoryService.create(maria.getId(), "Sorveteria", TransactionType.INCOME).id();

        excluir(joao, sorveteriaDaMaria)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(categoryRepository.findById(sorveteriaDaMaria)).isPresent();
    }

    @Test
    void naoDeveExcluirOsModelosPadraoPelaApi() throws Exception {
        UUID modelo = categoryRepository.findByUserIdIsNull().getFirst().getId();

        excluir(maria, modelo).andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(modelo)).isPresent();
    }

    @Test
    void deveRetornarNaoEncontradoParaCategoriaInexistente() throws Exception {
        excluir(maria, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void deveExigirAutenticacaoParaExcluir() throws Exception {
        mockMvc.perform(delete(CATEGORIES_PATH + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private User criarUsuario(String email) {
        User usuario = userRepository.save(new User("Usuário de teste", email, "hash"));
        categoryService.createDefaultCategories(usuario.getId());
        return usuario;
    }

    private Transaction lancar(User usuario, UUID categoria, String valor, String data, String descricao) {
        return transactionRepository.save(new Transaction(
                usuario.getId(), categoria, TransactionType.EXPENSE, new BigDecimal(valor), LocalDate.parse(data), descricao));
    }

    private UUID idDaCategoria(User usuario, String nome) {
        return categoryRepository.findByUserIdOrderByTypeAscNameAsc(usuario.getId()).stream()
                .filter(categoria -> categoria.getName().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    private ResultActions excluir(User usuario, UUID id) throws Exception {
        return mockMvc.perform(delete(CATEGORIES_PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(usuario)));
    }

    private String bearer(User usuario) {
        return "Bearer " + jwtService.generateToken(usuario);
    }
}
