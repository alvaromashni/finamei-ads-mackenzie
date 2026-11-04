package br.com.finamei.accountantaccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountantAccessRepository extends JpaRepository<AccountantAccess, UUID> {

	List<AccountantAccess> findByMeiIdAndRevokedAtIsNullOrderByGrantedAtAsc(UUID meiId);

	List<AccountantAccess> findByAccountantIdAndRevokedAtIsNull(UUID accountantId);

	List<AccountantAccess> findByAccountantIdAndRevokedAtIsNullOrderByGrantedAtAsc(UUID accountantId);

	Optional<AccountantAccess> findByMeiIdAndAccountantId(UUID meiId, UUID accountantId);

	Optional<AccountantAccess> findByIdAndMeiIdAndRevokedAtIsNull(UUID id, UUID meiId);

	boolean existsByMeiIdAndAccountantIdAndRevokedAtIsNull(UUID meiId, UUID accountantId);
}
