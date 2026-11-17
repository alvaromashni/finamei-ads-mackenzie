package br.com.finamei.auth;

import br.com.finamei.auth.dto.RegisterUserRequest;
import br.com.finamei.category.CategoryService;
import br.com.finamei.shared.error.EmailAlreadyRegisteredException;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final CategoryService categoryService;

	public RegisterUserService(
			UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryService categoryService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.categoryService = categoryService;
	}

	@Transactional
	public User register(RegisterUserRequest request) {
		String email = request.email().trim().toLowerCase();

		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}

		Role role = request.role() == null ? Role.MEI : request.role();
		if (role == Role.ADMIN) {
			throw new FieldValidationException("role", "Escolha o perfil de microempreendedor ou de contador.");
		}

		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), role);

		User saved;
		try {
			saved = userRepository.save(user);
		} catch (DataIntegrityViolationException ex) {
			throw new EmailAlreadyRegisteredException();
		}

		// Cada MEI começa com a própria cópia das categorias padrão (OF10).
		// Contador não lança receitas nem despesas, então não recebe categorias.
		if (role == Role.MEI) {
			categoryService.createDefaultCategories(saved.getId());
		}
		return saved;
	}
}
