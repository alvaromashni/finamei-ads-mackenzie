package br.com.finamei.category;

import br.com.finamei.shared.error.CategoryAlreadyExistsException;
import br.com.finamei.shared.error.CategoryInUseException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categorias do usuário autenticado (OF10, RN07). O userId vem sempre do token. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public CategoryService(CategoryRepository categoryRepository, TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Categorias do usuário do tipo informado (ou de todos, quando {@code null}),
     * incluindo as inativas: o cliente usa o campo {@code active} para decidir o que oferecer.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, TransactionType type) {
        List<Category> categories = type == null
                ? categoryRepository.findByUserIdOrderByTypeAscNameAsc(userId)
                : categoryRepository.findByUserIdAndTypeOrderByNameAsc(userId, type);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    /** Copia as categorias padrão para um novo usuário. */
    @Transactional
    public void createDefaultCategories(UUID userId) {
        List<Category> copies = categoryRepository.findByUserIdIsNull().stream()
                .map(template -> Category.copyOf(template, userId))
                .toList();
        categoryRepository.saveAll(copies);
    }

    @Transactional
    public CategoryResponse create(UUID userId, String name, TransactionType type) {
        String trimmed = name.trim();
        if (categoryRepository.existsByUserIdAndTypeAndNameIgnoreCase(userId, type, trimmed)) {
            throw new CategoryAlreadyExistsException();
        }
        try {
            return CategoryResponse.from(categoryRepository.saveAndFlush(new Category(userId, trimmed, type)));
        } catch (DataIntegrityViolationException ex) {
            // Mesmo nome criado em paralelo: a constraint única do banco decide.
            throw new CategoryAlreadyExistsException();
        }
    }

    @Transactional
    public CategoryResponse rename(UUID userId, UUID categoryId, String name) {
        Category category = findOwned(userId, categoryId);
        String trimmed = name.trim();
        if (categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                userId, category.getType(), trimmed, categoryId)) {
            throw new CategoryAlreadyExistsException();
        }
        category.rename(trimmed);
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryAlreadyExistsException();
        }
        return CategoryResponse.from(category);
    }

    /** Inativa ou reativa. Uma categoria inativa continua nos lançamentos já registrados (RN07). */
    @Transactional
    public CategoryResponse setActive(UUID userId, UUID categoryId, boolean active) {
        Category category = findOwned(userId, categoryId);
        if (active) {
            category.activate();
        } else {
            category.deactivate();
        }
        return CategoryResponse.from(category);
    }

    /**
     * Exclui uma categoria que nunca foi usada. Com lançamentos vinculados, inclusive os excluídos
     * logicamente, só é possível inativá-la (RN07).
     */
    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        Category category = findOwned(userId, categoryId);
        if (transactionRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException();
        }
        categoryRepository.delete(category);
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Lançamento criado em paralelo: a chave estrangeira do banco decide.
            throw new CategoryInUseException();
        }
    }

    // Categoria de outro usuário responde 404, sem revelar que existe (ONF05).
    private Category findOwned(UUID userId, UUID categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
    }
}
