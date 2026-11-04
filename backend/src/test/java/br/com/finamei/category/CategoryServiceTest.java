package br.com.finamei.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.finamei.shared.error.CategoryAlreadyExistsException;
import br.com.finamei.shared.error.CategoryInUseException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void deveExcluirCategoriaSemLancamentosVinculados() {
        Category categoria = new Category(userId, "Sorveteria", TransactionType.INCOME);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));
        when(transactionRepository.existsByCategoryId(categoryId)).thenReturn(false);

        categoryService.delete(userId, categoryId);

        verify(categoryRepository).delete(categoria);
    }

    @Test
    void naoDeveExcluirCategoriaComLancamentosVinculados() {
        Category categoria = new Category(userId, "Aluguel", TransactionType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));
        when(transactionRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(userId, categoryId))
                .isInstanceOf(CategoryInUseException.class);

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deveRecusarExclusaoQuandoOBancoRejeitaPorLancamentoCriadoEmParalelo() {
        Category categoria = new Category(userId, "Sorveteria", TransactionType.INCOME);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));
        when(transactionRepository.existsByCategoryId(categoryId)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_transactions_category")).when(categoryRepository).flush();

        assertThatThrownBy(() -> categoryService.delete(userId, categoryId))
                .isInstanceOf(CategoryInUseException.class);
    }

    @Test
    void naoDeveExcluirCategoriaDeOutroUsuario() {
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(userId, categoryId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(transactionRepository);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deveInativarCategoriaSemExcluirNada() {
        Category categoria = new Category(userId, "Aluguel", TransactionType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));

        CategoryResponse resposta = categoryService.setActive(userId, categoryId, false);

        assertThat(resposta.active()).isFalse();
        assertThat(categoria.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any(Category.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void deveReativarCategoriaInativa() {
        Category categoria = new Category(userId, "Aluguel", TransactionType.EXPENSE);
        categoria.deactivate();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));

        CategoryResponse resposta = categoryService.setActive(userId, categoryId, true);

        assertThat(resposta.active()).isTrue();
    }

    @Test
    void categoriaInativaNaoDeveAceitarNovosLancamentosMasAAtivaDeveAceitarOSeuTipo() {
        Category despesa = new Category(userId, "Aluguel", TransactionType.EXPENSE);

        assertThat(despesa.acceptsNewTransactionOf(TransactionType.EXPENSE)).isTrue();
        assertThat(despesa.acceptsNewTransactionOf(TransactionType.INCOME)).isFalse();

        despesa.deactivate();

        assertThat(despesa.acceptsNewTransactionOf(TransactionType.EXPENSE)).isFalse();
    }

    @Test
    void deveListarAsCategoriasDoUsuarioIncluindoAsInativas() {
        Category ativa = new Category(userId, "Aluguel", TransactionType.EXPENSE);
        Category inativa = new Category(userId, "Transporte", TransactionType.EXPENSE);
        inativa.deactivate();
        when(categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, TransactionType.EXPENSE))
                .thenReturn(List.of(ativa, inativa));

        List<CategoryResponse> resposta = categoryService.list(userId, TransactionType.EXPENSE);

        assertThat(resposta).extracting(CategoryResponse::name).containsExactly("Aluguel", "Transporte");
        assertThat(resposta).extracting(CategoryResponse::active).containsExactly(true, false);
    }

    @Test
    void naoDeveCriarCategoriaComNomeRepetidoNoMesmoTipo() {
        when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCase(userId, TransactionType.EXPENSE, "Aluguel"))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(userId, "  Aluguel  ", TransactionType.EXPENSE))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void naoDeveRenomearParaNomeJaUsadoNoMesmoTipo() {
        Category categoria = new Category(userId, "Transporte", TransactionType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.of(categoria));
        when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                        userId, TransactionType.EXPENSE, "Aluguel", categoryId))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.rename(userId, categoryId, "Aluguel"))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        assertThat(categoria.getName()).isEqualTo("Transporte");
    }
}
