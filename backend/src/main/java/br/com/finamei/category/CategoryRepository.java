package br.com.finamei.category;

import br.com.finamei.transaction.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdOrderByTypeAscNameAsc(UUID userId);

    List<Category> findByUserIdAndTypeOrderByNameAsc(UUID userId, TransactionType type);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    /** Modelos padrão, copiados para cada usuário no cadastro. */
    List<Category> findByUserIdIsNull();

    long countByUserId(UUID userId);

    boolean existsByUserIdAndTypeAndNameIgnoreCase(UUID userId, TransactionType type, String name);

    boolean existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(UUID userId, TransactionType type, String name, UUID id);
}
