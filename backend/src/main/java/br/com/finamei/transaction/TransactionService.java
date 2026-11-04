package br.com.finamei.transaction;

import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.category.Category;
import br.com.finamei.category.CategoryRepository;
import br.com.finamei.shared.PageResponse;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.dto.BalanceSummaryResponse;
import br.com.finamei.transaction.dto.TransactionRequest;
import br.com.finamei.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;

    // Mais recentes primeiro; no mesmo dia, o último registrado aparece antes.
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"));

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;
    private final AuditService auditService;

    public TransactionService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            Clock clock,
            AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    /** O userId vem da identidade autenticada; o cliente nunca informa o dono do lançamento. */
    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        validateDate(request);
        Category category = validCategoryFor(userId, request, null);

        Transaction transaction = transactionRepository.save(new Transaction(
                userId,
                category.getId(),
                request.type(),
                request.amount(),
                request.date(),
                request.description().trim()));
        auditService.record(userId, AuditAction.CREATE, AuditResourceType.TRANSACTION,
                transaction.getId(), describe(transaction));
        return TransactionResponse.from(transaction, category);
    }

    /**
     * Edição (OF06, OF08) com as mesmas regras do cadastro. O lançamento pode manter
     * a categoria atual mesmo que ela tenha sido inativada depois (RN07).
     */
    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, TransactionRequest request) {
        Transaction transaction = findActiveOwned(userId, transactionId);
        validateDate(request);
        Category category = validCategoryFor(userId, request, transaction.getCategoryId());
        String before = describe(transaction);

        transaction.update(
                category.getId(),
                request.type(),
                request.amount(),
                request.date(),
                request.description().trim());
        auditService.record(userId, AuditAction.UPDATE, AuditResourceType.TRANSACTION,
                transaction.getId(), "before[" + before + "] after[" + describe(transaction) + "]");
        return TransactionResponse.from(transaction, category);
    }

    /** Exclusão lógica (RN08): o lançamento sai da lista, do saldo e do faturamento. */
    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transaction transaction = findActiveOwned(userId, transactionId);
        transaction.markAsDeleted();
        auditService.record(userId, AuditAction.DELETE, AuditResourceType.TRANSACTION,
                transaction.getId(), describe(transaction));
    }

    // So valores de negocio para rastreio; a descricao e texto livre do usuario e fica de fora.
    private static String describe(Transaction transaction) {
        return "type=" + transaction.getType() + ", amount=" + transaction.getAmount()
                + ", date=" + transaction.getTransactionDate() + ", categoryId=" + transaction.getCategoryId();
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, int page, int size, TransactionFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new FieldValidationException("to", "A data final deve ser igual ou posterior à data inicial.");
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        Page<Transaction> transactions = transactionRepository.findAll(
                TransactionSpecifications.activeOfUser(userId, filter), pageRequest);

        Set<UUID> categoryIds = transactions.stream().map(Transaction::getCategoryId).collect(Collectors.toSet());
        Map<UUID, Category> categories = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return PageResponse.from(transactions, t -> TransactionResponse.from(t, categories.get(t.getCategoryId())));
    }

    /** Saldo atual e entradas e saídas do mês. Despesas reduzem o saldo, mas não o faturamento (RN04). */
    @Transactional(readOnly = true)
    public BalanceSummaryResponse summary(UUID userId, YearMonth month) {
        BigDecimal totalIncome = sum(transactionRepository.sumAmountByUserAndType(userId, TransactionType.INCOME));
        BigDecimal totalExpense = sum(transactionRepository.sumAmountByUserAndType(userId, TransactionType.EXPENSE));
        return new BalanceSummaryResponse(
                month,
                totalIncome.subtract(totalExpense),
                sumOfMonth(userId, TransactionType.INCOME, month),
                sumOfMonth(userId, TransactionType.EXPENSE, month));
    }

    private BigDecimal sumOfMonth(UUID userId, TransactionType type, YearMonth month) {
        return sum(transactionRepository.sumAmountByUserAndTypeAndPeriod(
                userId, type, month.atDay(1), month.atEndOfMonth()));
    }

    private static BigDecimal sum(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }

    // Lançamento de outro usuário ou já excluído responde 404 (ONF05, UC 4c).
    private Transaction findActiveOwned(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserIdAndDeletedFalse(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
    }

    private void validateDate(TransactionRequest request) {
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw new FieldValidationException("date", "A data não pode ser futura.");
        }
    }

    // RN06 e RN07: a categoria precisa ser do usuário, do tipo do lançamento e estar ativa;
    // a categoria que o lançamento já usa é aceita mesmo inativa (currentCategoryId).
    // Categoria de outro usuário é tratada como inexistente (ONF05).
    private Category validCategoryFor(UUID userId, TransactionRequest request, UUID currentCategoryId) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new FieldValidationException("categoryId", "Selecione uma categoria válida."));
        if (category.getType() != request.type()) {
            throw new FieldValidationException(
                    "categoryId", "A categoria selecionada não corresponde ao tipo do lançamento.");
        }
        if (!category.isActive() && !category.getId().equals(currentCategoryId)) {
            throw new FieldValidationException(
                    "categoryId", "A categoria selecionada está inativa. Escolha outra categoria.");
        }
        return category;
    }
}
