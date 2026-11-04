package br.com.finamei.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

	Optional<LoginAttempt> findByEmail(String email);

	List<LoginAttempt> findByEmailIn(Collection<String> emails);
}
