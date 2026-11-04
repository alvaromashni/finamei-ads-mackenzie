package br.com.finamei.category;

import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.shared.error.CategoryAlreadyExistsException;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.TransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categorias do usuário autenticado (OF10, RN07). O userId vem sempre do token. */
@Service
public class CategoryService {

    /**
     * Teto de categorias por usuário. A listagem de categorias não é paginada (o cliente precisa
     * de todas para montar os seletores); este limite garante que ela nunca seja ilimitada.
     * As categorias padrão são cerca de 20, então 200 não atrapalha o uso real.
     */
    static final int MAX_CATEGORIES_PER_USER = 200;

    private final CategoryRepository categoryRepository;

    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
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
        if (categoryRepository.countByUserId(userId) >= MAX_CATEGORIES_PER_USER) {
            throw new FieldValidationException(
                    "name", "Limite de " + MAX_CATEGORIES_PER_USER + " categorias atingido. Exclua ou reutilize uma existente.");
        }
        try {
            Category category = categoryRepository.saveAndFlush(new Category(userId, trimmed, type));
            auditService.record(userId, AuditAction.CREATE, AuditResourceType.CATEGORY,
                    category.getId(), "name=" + category.getName() + ", type=" + type);
            return CategoryResponse.from(category);
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
        String previousName = category.getName();
        category.rename(trimmed);
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryAlreadyExistsException();
        }
        auditService.record(userId, AuditAction.UPDATE, AuditResourceType.CATEGORY,
                categoryId, "name: " + previousName + " -> " + trimmed);
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
        auditService.record(userId, AuditAction.UPDATE, AuditResourceType.CATEGORY,
                categoryId, "active=" + active);
        return CategoryResponse.from(category);
    }

    // Categoria de outro usuário responde 404, sem revelar que existe (ONF05).
    private Category findOwned(UUID userId, UUID categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
    }
}
