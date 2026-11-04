package br.com.finamei.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /** Lançamento ativo (não excluído, RN08) que pertence ao usuário. */
    Optional<Transaction> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    /**
     * Totais dos lançamentos ativos do usuário por tipo, em uma única consulta: o total geral e o
     * total do período informado (inclusive). Tipos sem lançamentos não aparecem; os somas podem
     * ser {@code null} quando não há valores.
     */
    @Query("""
            select new br.com.finamei.transaction.TypeTotals(
                t.type,
                sum(t.amount),
                sum(case when t.transactionDate between :start and :end then t.amount else null end))
            from Transaction t
            where t.userId = :userId
              and t.deleted = false
            group by t.type
            """)
    List<TypeTotals> totalsByType(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Soma e quantidade dos lançamentos ativos do usuário por tipo, dentro do período (inclusive),
     * em uma única consulta. Tipos sem lançamentos no período não aparecem.
     */
    @Query("""
            select new br.com.finamei.transaction.PeriodTypeTotals(t.type, sum(t.amount), count(t))
            from Transaction t
            where t.userId = :userId
              and t.deleted = false
              and t.transactionDate between :start and :end
            group by t.type
            """)
    List<PeriodTypeTotals> totalsByTypeInPeriod(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Soma os valores dos lançamentos ativos (não excluídos) de um usuário, de um tipo,
     * dentro do período informado (inclusive). Retorna {@code null} quando não há lançamentos.
     */
    @Query("""
            select sum(t.amount)
            from Transaction t
            where t.userId = :userId
              and t.type = :type
              and t.transactionDate between :start and :end
              and t.deleted = false
            """)
    BigDecimal sumAmountByUserAndTypeAndPeriod(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Lançamentos ativos do usuário no período (inclusive), em ordem estável (data, id), com o nome
     * da categoria, para exportação. Paginado: quem exporta lê por páginas e impõe um teto de linhas.
     */
    @Query("""
            select new br.com.finamei.transaction.ExportRow(
                t.transactionDate, t.type, c.name, t.description, t.amount)
            from Transaction t
            left join Category c on c.id = t.categoryId
            where t.userId = :userId
              and t.deleted = false
              and t.transactionDate between :start and :end
            order by t.transactionDate, t.id
            """)
    Slice<ExportRow> findExportRows(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            Pageable pageable);
}
