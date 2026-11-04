package br.com.finamei.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** A listagem de categorias não é paginada, então o total por usuário tem teto (ONF06). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryLimitTest {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void deveRecusarCategoriaAlemDoTetoPorUsuarioEManterAListagemLimitada() {
		User maria = userRepository.save(new User("Maria", "maria.limite@exemplo.com", "hash"));
		for (int i = 0; i < CategoryService.MAX_CATEGORIES_PER_USER; i++) {
			categoryService.create(maria.getId(), "Categoria " + i, TransactionType.EXPENSE);
		}

		assertThatThrownBy(() -> categoryService.create(maria.getId(), "Uma a mais", TransactionType.EXPENSE))
				.isInstanceOf(FieldValidationException.class)
				.hasMessageContaining("200");

		assertThat(categoryService.list(maria.getId(), null)).hasSize(CategoryService.MAX_CATEGORIES_PER_USER);
	}

	@Test
	void umUsuarioNaoConsomeOTetoDeOutro() {
		User maria = userRepository.save(new User("Maria", "maria.teto@exemplo.com", "hash"));
		User joao = userRepository.save(new User("João", "joao.teto@exemplo.com", "hash"));
		categoryService.createDefaultCategories(maria.getId());

		CategoryResponse criada = categoryService.create(joao.getId(), "Primeira", TransactionType.INCOME);

		assertThat(criada.name()).isEqualTo("Primeira");
	}
}
