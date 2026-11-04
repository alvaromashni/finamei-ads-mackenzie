package br.com.finamei.user;

import br.com.finamei.shared.error.EmailAlreadyRegisteredException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.user.dto.UpdateProfileRequest;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

	private final UserRepository userRepository;

	public ProfileService(UserRepository userRepository) {
		this.userRepository = userRepository;
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

		user.updateProfile(request.name().trim(), email);
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
