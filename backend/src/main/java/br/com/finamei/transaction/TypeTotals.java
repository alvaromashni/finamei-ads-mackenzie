package br.com.finamei.transaction;

import java.math.BigDecimal;

/**
 * Totais de um tipo de lançamento: o total geral e o do período consultado (resumo de saldo).
 * Os valores são {@code null} quando não há lançamentos.
 */
public record TypeTotals(TransactionType type, BigDecimal total, BigDecimal monthTotal) {

    static final TypeTotals EMPTY_INCOME = new TypeTotals(TransactionType.INCOME, null, null);
    static final TypeTotals EMPTY_EXPENSE = new TypeTotals(TransactionType.EXPENSE, null, null);
}
