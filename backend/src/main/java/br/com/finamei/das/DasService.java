package br.com.finamei.das;

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
import java.util.Comparator;
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

	public DasService(
			DasGuideRepository repository,
			DasDueDateCalculator dueDateCalculator,
			Clock clock,
			@Value("${finamei.das.monthly-amount}") BigDecimal monthlyAmount) {
		if (monthlyAmount.signum() <= 0) {
			throw new IllegalArgumentException("finamei.das.monthly-amount must be greater than zero");
		}
		this.repository = repository;
		this.dueDateCalculator = dueDateCalculator;
		this.clock = clock;
		this.monthlyAmount = monthlyAmount;
	}

	/**
	 * Guides of the year, January first. The 12 guides of the current year are
	 * created on the first access, without duplicating the ones that already exist.
	 */
	@Transactional
	public List<DasGuideResponse> listGuides(UUID userId, int year) {
		LocalDate today = LocalDate.now(clock);
		List<DasGuide> guides = findGuides(userId, year);
		if (year == today.getYear()) {
			List<DasGuide> created = createMissingGuides(userId, year, guides);
			if (!created.isEmpty()) {
				// Reaproveita a consulta já feita em vez de buscar tudo de novo.
				guides = new ArrayList<>(guides);
				guides.addAll(created);
				guides.sort(Comparator.comparing(DasGuide::getCompetence));
			}
		}
		return guides.stream()
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
		return DasGuideResponse.from(guide, LocalDate.now(clock));
	}

	private DasGuide findOwned(UUID userId, UUID guideId) {
		return repository.findByIdAndUserId(guideId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Guia do DAS não encontrada."));
	}

	private List<DasGuide> createMissingGuides(UUID userId, int year, List<DasGuide> current) {
		Set<YearMonth> existing = current.stream()
				.map(DasGuide::getCompetence)
				.collect(Collectors.toSet());

		List<DasGuide> missing = new ArrayList<>();
		for (Month month : Month.values()) {
			YearMonth competence = YearMonth.of(year, month);
			if (!existing.contains(competence)) {
				missing.add(new DasGuide(userId, competence, dueDateCalculator.dueDateOf(competence), monthlyAmount));
			}
		}
		return repository.saveAll(missing);
	}

	private List<DasGuide> findGuides(UUID userId, int year) {
		return repository.findByUserIdAndCompetenceBetweenOrderByCompetenceAsc(
				userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 1));
	}
}
