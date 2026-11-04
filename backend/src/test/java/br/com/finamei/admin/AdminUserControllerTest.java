package br.com.finamei.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.audit.AuditAction;
import br.com.finamei.audit.AuditLog;
import br.com.finamei.audit.AuditLogRepository;
import br.com.finamei.audit.AuditResourceType;
import br.com.finamei.auth.LoginAttempt;
import br.com.finamei.auth.LoginAttemptRepository;
import br.com.finamei.security.JwtService;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserControllerTest {

	private static final String PATH = "/api/v1/admin/users";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private Clock clock;

	private User admin;
	private User maria;

	@BeforeEach
	void criarUsuarios() {
		admin = criar("Admin", "admin.gestao@exemplo.com", Role.ADMIN);
		maria = criar("Maria", "maria.gestao@exemplo.com", Role.MEI);
	}

	@Test
	void deveListarContasPaginadasSemExporHash() throws Exception {
		mockMvc.perform(get(PATH).param("size", "2").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.number").value(0))
				.andExpect(jsonPath("$.totalElements").value(userRepository.count()))
				.andExpect(jsonPath("$.content[0].id").exists())
				.andExpect(jsonPath("$.content[0].name").exists())
				.andExpect(jsonPath("$.content[0].email").exists())
				.andExpect(jsonPath("$.content[0].role").exists())
				.andExpect(jsonPath("$.content[0].active").exists())
				.andExpect(jsonPath("$.content[0].createdAt").exists())
				.andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
				.andExpect(jsonPath("$.content[0].password").doesNotExist());
	}

	@Test
	void deveConsultarStatusDeUmaConta() throws Exception {
		mockMvc.perform(get(PATH + "/" + maria.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(maria.getId().toString()))
				.andExpect(jsonPath("$.email").value("maria.gestao@exemplo.com"))
				.andExpect(jsonPath("$.role").value("MEI"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.loginLocked").value(false))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void deveDesativarEReativarContaEAuditarCadaAlteracao() throws Exception {
		alterarStatus(admin, maria, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
		alterarStatus(admin, maria, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		List<AuditLog> registros = auditLogRepository.findByResourceTypeAndResourceId(AuditResourceType.USER, maria.getId());
		assertThat(registros).hasSize(2).allSatisfy(registro -> {
			assertThat(registro.getActorUserId()).isEqualTo(admin.getId());
			assertThat(registro.getAction()).isEqualTo(AuditAction.UPDATE);
			assertThat(registro.getOccurredAt()).isNotNull();
		});
		assertThat(registros).extracting(AuditLog::getDetails).containsExactlyInAnyOrder("deactivated", "activated");
	}

	@Test
	void contaDesativadaNaoConseguePassarNoFiltroMesmoComTokenAindaValido() throws Exception {
		String tokenDaMaria = bearer(maria);
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, tokenDaMaria))
				.andExpect(status().isOk());

		alterarStatus(admin, maria, false).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, tokenDaMaria))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		alterarStatus(admin, maria, true).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, tokenDaMaria))
				.andExpect(status().isOk());
	}

	@Test
	void deveLiberarBloqueioDeLoginEAuditar() throws Exception {
		LoginAttempt tentativa = new LoginAttempt(maria.getEmail(), clock.instant());
		for (int i = 0; i < 5; i++) {
			tentativa.registerFailure(clock.instant(), 5, Duration.ofMinutes(15));
		}
		loginAttemptRepository.saveAndFlush(tentativa);

		mockMvc.perform(get(PATH + "/" + maria.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(jsonPath("$.loginLocked").value(true))
				.andExpect(jsonPath("$.loginLockedUntil").exists());

		mockMvc.perform(post(PATH + "/" + maria.getId() + "/login-unlock")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginLocked").value(false))
				.andExpect(jsonPath("$.loginLockedUntil").doesNotExist());

		assertThat(loginAttemptRepository.findByEmail(maria.getEmail())).isEmpty();
		assertThat(auditLogRepository.findByResourceTypeAndResourceId(AuditResourceType.USER, maria.getId()))
				.singleElement()
				.satisfies(registro -> {
					assertThat(registro.getActorUserId()).isEqualTo(admin.getId());
					assertThat(registro.getDetails()).isEqualTo("login lock cleared");
				});
	}

	@Test
	void administradorNaoPodeDesativarASiMesmo() throws Exception {
		alterarStatus(admin, admin, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_OWN_ACCOUNT"));

		assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
		assertThat(auditLogRepository.countByActorUserId(admin.getId())).isZero();
	}

	@Test
	void deveValidarOCorpoEOUsuarioDaAlteracaoDeStatus() throws Exception {
		mockMvc.perform(patch(PATH + "/" + maria.getId() + "/status")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.active").exists());

		mockMvc.perform(patch(PATH + "/" + UUID.randomUUID() + "/status")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\": false}"))
				.andExpect(status().isNotFound());

		assertThat(auditLogRepository.countByActorUserId(admin.getId())).isZero();
	}

	@Test
	void somenteAdministradorAcessaOsEndpoints() throws Exception {
		User contador = criar("Ana", "ana.gestao@contabil.com", Role.ACCOUNTANT);
		for (User intruso : List.of(maria, contador)) {
			String auth = bearer(intruso);
			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
			mockMvc.perform(get(PATH + "/" + maria.getId()).header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isForbidden());
			alterarStatus(intruso, maria, false).andExpect(status().isForbidden());
			mockMvc.perform(post(PATH + "/" + maria.getId() + "/login-unlock").header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isForbidden());
		}
		assertThat(userRepository.findById(maria.getId()).orElseThrow().isActive()).isTrue();
		assertThat(auditLogRepository.countByActorUserId(maria.getId())).isZero();
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(PATH + "/" + maria.getId())).andExpect(status().isUnauthorized());
		mockMvc.perform(patch(PATH + "/" + maria.getId() + "/status")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\": false}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH + "/" + maria.getId() + "/login-unlock")).andExpect(status().isUnauthorized());
	}

	@Test
	void naoDeveExistirRemocaoDeContas() throws Exception {
		mockMvc.perform(delete(PATH + "/" + maria.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().is4xxClientError());
		assertThat(userRepository.existsById(maria.getId())).isTrue();
	}

	private ResultActions alterarStatus(User autor, User alvo, boolean ativo) throws Exception {
		return mockMvc.perform(patch(PATH + "/" + alvo.getId() + "/status")
				.header(HttpHeaders.AUTHORIZATION, bearer(autor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\": " + ativo + "}"));
	}

	private User criar(String nome, String email, Role role) {
		User usuario = new User(nome, email, "hash");
		ReflectionTestUtils.setField(usuario, "role", role);
		return userRepository.save(usuario);
	}

	private String bearer(User usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}
}
