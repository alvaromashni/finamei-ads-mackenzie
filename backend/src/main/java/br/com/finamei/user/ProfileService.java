package br.com.finamei.user;

import br.com.finamei.shared.error.EmailAlreadyRegisteredException;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.dto.UpdateProfileRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final Clock clock;

	public ProfileService(UserRepository userRepository, Clock clock) {
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public User getProfile(UUID userId) {
		return find(userId);
	}

	@Transactional
	public User updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = find(userId);
		String email = request.email().trim().toLowerCase();

		boolean emailTakenByAnother = userRepository.findByEmail(email)
				.filter(other -> !other.getId().equals(userId))
				.isPresent();
		if (emailTakenByAnother) {
			throw new EmailAlreadyRegisteredException();
		}

		LocalDate openingDate = request.meiOpeningDate();
		if (openingDate != null && openingDate.isAfter(LocalDate.now(clock))) {
			throw new FieldValidationException("meiOpeningDate", "A data de abertura não pode ser futura.");
		}

		user.updateProfile(request.name().trim(), email);
		if (Boolean.TRUE.equals(request.clearMeiOpeningDate())) {
			user.changeMeiOpeningDate(null);
		} else if (openingDate != null) {
			user.changeMeiOpeningDate(openingDate);
		}
		try {
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			// Corrida entre duas alterações: a restrição única do banco é a palavra final.
			throw new EmailAlreadyRegisteredException();
		}
	}

	private User find(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
	}
}
