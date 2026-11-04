package br.com.finamei.transaction;

import java.math.BigDecimal;

/** Soma e quantidade de lançamentos ativos de um tipo dentro de um período (relatórios). */
public record PeriodTypeTotals(TransactionType type, BigDecimal total, long count) {
}
