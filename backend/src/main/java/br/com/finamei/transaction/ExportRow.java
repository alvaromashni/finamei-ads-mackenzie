package br.com.finamei.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha de lançamento para exportação de relatório; a categoria vem junto para evitar N+1. */
public record ExportRow(
		LocalDate date, TransactionType type, String category, String description, BigDecimal amount) {
}
