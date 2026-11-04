package br.com.finamei.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	/** Usado a cada requisição autenticada: consulta só o necessário. */
	boolean existsByIdAndActiveFalse(UUID id);
}
