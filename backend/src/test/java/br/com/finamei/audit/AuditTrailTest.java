package br.com.finamei.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.finamei.accountantaccess.AccountantAccessService;
import br.com.finamei.accountantaccess.dto.AccountantAccessResponse;
import br.com.finamei.category.CategoryResponse;
import br.com.finamei.category.CategoryService;
import br.com.finamei.das.DasService;
import br.com.finamei.das.dto.DasGuideResponse;
import br.com.finamei.shared.error.FieldValidationException;
import br.com.finamei.shared.error.ResourceNotFoundException;
import br.com.finamei.transaction.TransactionRepository;
import br.com.finamei.transaction.TransactionService;
import br.com.finamei.transaction.TransactionType;
import br.com.finamei.transaction.dto.TransactionRequest;
import br.com.finamei.transaction.dto.TransactionResponse;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ONF10: cada escrita gera exatamente um registro de auditoria, na mesma transação.
 * Não usa @Transactional na classe: precisa de commits e rollbacks reais para provar que não
 * sobra auditoria órfã. Os dados criados são apagados no fim de cada teste.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditTrailTest {

	private static final Instant AGORA = Instant.parse("2026-09-15T13:00:00Z");

	@TestBean
	private Clock clock;

	/** Relógio que avança 1 s a cada leitura, para os registros terem instantes distintos e ordenáveis. */
	static Clock clock() {
		return new Clock() {
			private final AtomicLong leituras = new AtomicLong();

			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return AGORA.plusSeconds(leituras.getAndIncrement());
			}
		};
	}

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private DasService dasService;

	@Autowired
	private AccountantAccessService accountantAccessService;

	@Autowired
	private AuditService auditService;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<UUID> usuariosCriados = new ArrayList<>();
	private User maria;
	private UUID categoriaDeReceita;

	@BeforeEach
	void criarUsuario() {
		maria = criarUsuario("maria.auditoria." + UUID.randomUUID() + "@exemplo.com", Role.MEI);
		categoryService.createDefaultCategories(maria.getId());
		categoriaDeReceita = categoryService.list(maria.getId(), TransactionType.INCOME).get(0).id();
	}

	@AfterEach
	void limparDados() {
		for (UUID id : usuariosCriados) {
			jdbcTemplate.update("delete from audit_logs where actor_user_id = ?", id);
			jdbcTemplate.update("delete from accountant_access where mei_id = ?", id);
			jdbcTemplate.update("delete from das_guides where user_id = ?", id);
			jdbcTemplate.update("delete from transactions where user_id = ?", id);
			jdbcTemplate.update("delete from categories where user_id = ?", id);
		}
		for (UUID id : usuariosCriados) {
			jdbcTemplate.update("delete from users where id = ?", id);
		}
	}

	@Test
	void deveAuditarCriacaoEdicaoEExclusaoDeLancamentoComUmRegistroCada() {
		TransactionResponse criado = transactionService.create(maria.getId(), lancamento("500.00", "Venda do dia"));
		assertThat(registros()).hasSize(1);

		transactionService.update(maria.getId(), criado.id(), lancamento("650.25", "Venda corrigida"));
		assertThat(registros()).hasSize(2);

		transactionService.delete(maria.getId(), criado.id());

		List<AuditLog> registros = registros();
		assertThat(registros).hasSize(3);
		assertThat(registros).extracting(AuditLog::getAction)
				.containsExactly(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
		assertThat(registros).allSatisfy(r -> {
			assertThat(r.getActorUserId()).isEqualTo(maria.getId());
			assertThat(r.getResourceType()).isEqualTo(AuditResourceType.TRANSACTION);
			assertThat(r.getResourceId()).isEqualTo(criado.id());
			assertThat(r.getOccurredAt()).isAfterOrEqualTo(AGORA);
			// A descrição é texto livre do usuário e não deve ir para a trilha.
			assertThat(r.getDetails()).doesNotContain("Venda");
		});
		assertThat(registros.get(0).getDetails()).contains("type=INCOME", "amount=500.00", "date=2026-09-10");
		assertThat(registros.get(1).getDetails()).contains("before[", "amount=500.00", "after[", "amount=650.25");
	}

	@Test
	void deveAuditarCriacaoRenomeacaoEMudancaDeStatusDeCategoria() {
		CategoryResponse criada = categoryService.create(maria.getId(), "Consultoria", TransactionType.INCOME);
		categoryService.rename(maria.getId(), criada.id(), "Consultoria técnica");
		categoryService.setActive(maria.getId(), criada.id(), false);

		List<AuditLog> registros = auditLogRepository.findByResourceTypeAndResourceId(
				AuditResourceType.CATEGORY, criada.id());
		assertThat(registros).hasSize(3);
		assertThat(registros).extracting(AuditLog::getAction)
				.containsExactlyInAnyOrder(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.UPDATE);
		assertThat(registros).extracting(AuditLog::getDetails).anySatisfy(
				d -> assertThat(d).contains("Consultoria -> Consultoria técnica"));
		assertThat(registros).allSatisfy(r -> assertThat(r.getActorUserId()).isEqualTo(maria.getId()));
	}

	@Test
	void deveAuditarPagamentoEDesfazimentoDoDasSemAuditarAGeracaoAutomaticaDeGuias() {
		List<DasGuideResponse> guias = dasService.listGuides(maria.getId(), 2026);
		assertThat(guias).hasSize(12);
		assertThat(registros()).isEmpty();

		UUID guia = guias.get(8).id();
		dasService.pay(maria.getId(), guia, LocalDate.of(2026, 9, 10));
		dasService.undoPayment(maria.getId(), guia);

		List<AuditLog> registros = registros();
		assertThat(registros).hasSize(2);
		assertThat(registros).allSatisfy(r -> {
			assertThat(r.getAction()).isEqualTo(AuditAction.UPDATE);
			assertThat(r.getResourceType()).isEqualTo(AuditResourceType.DAS_GUIDE);
			assertThat(r.getResourceId()).isEqualTo(guia);
		});
		assertThat(registros.get(0).getDetails()).contains("paid", "paidAt=2026-09-10");
		assertThat(registros.get(1).getDetails()).contains("payment undone");
	}

	@Test
	void deveAuditarConcessaoRevogacaoEReconcessaoDoAcessoDoContador() {
		User contador = criarUsuario("ana.auditoria." + UUID.randomUUID() + "@contabil.com", Role.ACCOUNTANT);

		AccountantAccessResponse acesso = accountantAccessService.grant(maria.getId(), contador.getEmail());
		accountantAccessService.revoke(maria.getId(), acesso.id());
		accountantAccessService.grant(maria.getId(), contador.getEmail());

		List<AuditLog> registros = registros();
		assertThat(registros).hasSize(3);
		assertThat(registros).extracting(AuditLog::getAction)
				.containsExactly(AuditAction.CREATE, AuditAction.DELETE, AuditAction.UPDATE);
		assertThat(registros).allSatisfy(r -> {
			assertThat(r.getResourceType()).isEqualTo(AuditResourceType.ACCOUNTANT_ACCESS);
			assertThat(r.getResourceId()).isEqualTo(acesso.id());
			assertThat(r.getDetails()).contains("accountantId=" + contador.getId());
		});
	}

	@Test
	void naoDeveDeixarAuditoriaOrfaQuandoATransacaoDaOperacaoDaRollback() {
		long lancamentosAntes = transactionRepository.count();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			transactionService.create(maria.getId(), lancamento("100.00", "Venda"));
			// Falha depois de a operação e a auditoria terem sido gravadas dentro da transação.
			throw new IllegalStateException("falha simulada");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(registros()).isEmpty();
		assertThat(transactionRepository.count()).isEqualTo(lancamentosAntes);
	}

	@Test
	void naoDeveAuditarOperacaoRejeitada() {
		TransactionRequest comCategoriaInvalida = new TransactionRequest(
				TransactionType.INCOME, new BigDecimal("10.00"), LocalDate.of(2026, 9, 10), UUID.randomUUID(), "x");

		assertThatThrownBy(() -> transactionService.create(maria.getId(), comCategoriaInvalida))
				.isInstanceOf(FieldValidationException.class);
		assertThatThrownBy(() -> transactionService.delete(maria.getId(), UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> categoryService.rename(maria.getId(), UUID.randomUUID(), "Nova"))
				.isInstanceOf(ResourceNotFoundException.class);

		assertThat(registros()).isEmpty();
	}

	@Test
	void auditoriaExigeTransacaoEmAndamento() {
		assertThatThrownBy(() -> auditService.record(
				maria.getId(), AuditAction.CREATE, AuditResourceType.TRANSACTION, UUID.randomUUID(), "x"))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(registros()).isEmpty();
	}

	@Test
	void registroDeAuditoriaEImutavelPelaAplicacao() {
		assertThat(CrudRepository.class.isAssignableFrom(AuditLogRepository.class)).isFalse();
		for (Method metodo : AuditLogRepository.class.getMethods()) {
			assertThat(metodo.getName()).doesNotStartWith("delete").doesNotStartWith("remove")
					.doesNotStartWith("update");
		}
		for (Method metodo : AuditLog.class.getMethods()) {
			assertThat(metodo.getName()).doesNotStartWith("set");
		}
	}

	@Test
	void naoDeveGuardarDadosSensiveisNaTrilha() {
		transactionService.create(maria.getId(), lancamento("10.00", "segredo-do-usuario"));
		categoryService.create(maria.getId(), "Outra", TransactionType.EXPENSE);

		assertThat(registros()).allSatisfy(r -> assertThat(r.getDetails().toLowerCase())
				.doesNotContain("hash", "password", "senha", "token", "segredo-do-usuario"));
	}

	private List<AuditLog> registros() {
		return auditLogRepository.findByActorUserIdOrderByOccurredAtAsc(maria.getId());
	}

	private TransactionRequest lancamento(String valor, String descricao) {
		return new TransactionRequest(
				TransactionType.INCOME, new BigDecimal(valor), LocalDate.of(2026, 9, 10), categoriaDeReceita, descricao);
	}

	private User criarUsuario(String email, Role role) {
		User usuario = new User("Usuário de teste", email, "hash");
		ReflectionTestUtils.setField(usuario, "role", role);
		User salvo = userRepository.save(usuario);
		usuariosCriados.add(salvo.getId());
		return salvo;
	}
}
