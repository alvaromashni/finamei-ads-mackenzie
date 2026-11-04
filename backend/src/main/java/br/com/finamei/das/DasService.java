package br.com.finamei.das;

import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.audit.AuditService;
import br.com.finamei.das.dto.DasGuideResponse;
import br.com.finamei.shared.error.DasAlreadyPaidException;
import br.com.finamei.shared.error.DasNotPaidException;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DasService {

	private final DasGuideRepository repository;
	private final DasDueDateCalculator dueDateCalculator;
	private final Clock clock;
	private final BigDecimal monthlyAmount;
	private final AuditService auditService;

	public DasService(
			DasGuideRepository repository,
			DasDueDateCalculator dueDateCalculator,
			Clock clock,
			@Value("${finamei.das.monthly-amount}") BigDecimal monthlyAmount,
			AuditService auditService) {
		if (monthlyAmount.signum() <= 0) {
			throw new IllegalArgumentException("finamei.das.monthly-amount must be greater than zero");
		}
		this.repository = repository;
		this.dueDateCalculator = dueDateCalculator;
		this.clock = clock;
		this.monthlyAmount = monthlyAmount;
		this.auditService = auditService;
	}

	/**
	 * Guides of the year, January first. The 12 guides of the current year are
	 * created on the first access, without duplicating the ones that already exist.
	 */
	@Transactional
	public List<DasGuideResponse> listGuides(UUID userId, int year) {
		LocalDate today = LocalDate.now(clock);
		if (year == today.getYear()) {
			createMissingGuides(userId, year);
		}
		return findGuides(userId, year).stream()
				.map(guide -> DasGuideResponse.from(guide, today))
				.toList();
	}

	@Transactional
	public DasGuideResponse pay(UUID userId, UUID guideId, LocalDate paidAt) {
		LocalDate today = LocalDate.now(clock);
		if (paidAt.isAfter(today)) {
			throw new FieldValidationException("paidAt", "A data do pagamento não pode ser futura.");
		}

		DasGuide guide = findOwned(userId, guideId);
		if (guide.isPaid()) {
			throw new DasAlreadyPaidException();
		}

		guide.markAsPaid(paidAt);
		auditService.record(userId, AuditAction.UPDATE, AuditResourceType.DAS_GUIDE, guide.getId(),
				"paid competence=" + guide.getCompetence() + ", paidAt=" + paidAt);
		return DasGuideResponse.from(guide, today);
	}

	/** Desfaz um pagamento lançado por engano (UC "Controlar DAS", 3a). */
	@Transactional
	public DasGuideResponse undoPayment(UUID userId, UUID guideId) {
		DasGuide guide = findOwned(userId, guideId);
		if (!guide.isPaid()) {
			throw new DasNotPaidException();
		}
		guide.markAsUnpaid();
		auditService.record(userId, AuditAction.UPDATE, AuditResourceType.DAS_GUIDE, guide.getId(),
				"payment undone competence=" + guide.getCompetence());
		return DasGuideResponse.from(guide, LocalDate.now(clock));
	}

	private DasGuide findOwned(UUID userId, UUID guideId) {
		return repository.findByIdAndUserId(guideId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Guia do DAS não encontrada."));
	}

	private void createMissingGuides(UUID userId, int year) {
		Set<YearMonth> existing = findGuides(userId, year).stream()
				.map(DasGuide::getCompetence)
				.collect(Collectors.toSet());

		List<DasGuide> missing = new ArrayList<>();
		for (Month month : Month.values()) {
			YearMonth competence = YearMonth.of(year, month);
			if (!existing.contains(competence)) {
				missing.add(new DasGuide(userId, competence, dueDateCalculator.dueDateOf(competence), monthlyAmount));
			}
		}
		repository.saveAll(missing);
	}

	private List<DasGuide> findGuides(UUID userId, int year) {
		return repository.findByUserIdAndCompetenceBetweenOrderByCompetenceAsc(
				userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 1));
	}
}
