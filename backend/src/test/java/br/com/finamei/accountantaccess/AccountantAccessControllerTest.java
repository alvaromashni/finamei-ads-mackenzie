package br.com.finamei.accountantaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finamei.security.JwtService;
import br.com.finamei.user.Role;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountantAccessControllerTest {

	private static final String PATH = "/api/v1/accountant-access";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountantAccessService accessService;

	@Test
	void deveConcederListarERevogarAcesso() throws Exception {
		User mei = criar("Maria", "maria.acesso@exemplo.com", Role.MEI);
		criar("Ana Contabilidade", "ana.acesso@contabil.com", Role.ACCOUNTANT);

		String corpo = mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"Ana.Acesso@contabil.com\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.accountant.name").value("Ana Contabilidade"))
				.andExpect(jsonPath("$.accountant.email").value("ana.acesso@contabil.com"))
				.andExpect(jsonPath("$.grantedAt").exists())
				.andExpect(jsonPath("$.accountant.passwordHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(corpo, "$.id");

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(id));

		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void revogacaoBloqueiaOAcessoDoContadorImediatamente() throws Exception {
		User mei = criar("Maria", "maria.revoga@exemplo.com", Role.MEI);
		User contador = criar("Ana", "ana.revoga@contabil.com", Role.ACCOUNTANT);

		String corpo = mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"ana.revoga@contabil.com\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(corpo, "$.id");

		assertThat(accessService.hasActiveAccess(contador.getId(), mei.getId())).isTrue();

		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(status().isNoContent());

		assertThat(accessService.hasActiveAccess(contador.getId(), mei.getId())).isFalse();
		assertThat(accessService.findActiveClientIds(contador.getId())).isEmpty();
	}

	@Test
	void devePermitirConcederDeNovoDepoisDeRevogar() throws Exception {
		User mei = criar("Maria", "maria.regrant@exemplo.com", Role.MEI);
		User contador = criar("Ana", "ana.regrant@contabil.com", Role.ACCOUNTANT);
		String id = conceder(mei, "ana.regrant@contabil.com");
		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(status().isNoContent());

		conceder(mei, "ana.regrant@contabil.com");

		assertThat(accessService.hasActiveAccess(contador.getId(), mei.getId())).isTrue();
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(mei)))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void deveResponder404QuandoNaoExisteContadorComOEmail() throws Exception {
		User mei = criar("Maria", "maria.404@exemplo.com", Role.MEI);
		criar("João MEI", "joao.mei@exemplo.com", Role.MEI);

		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"ninguem@exemplo.com\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Conta existente, mas que não é de contador, recebe a mesma resposta.
		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"joao.mei@exemplo.com\"}"))
				.andExpect(status().isNotFound());

		// E o MEI não consegue conceder acesso a si mesmo.
		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"maria.404@exemplo.com\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void deveResponder409QuandoOContadorJaTemAcesso() throws Exception {
		User mei = criar("Maria", "maria.409@exemplo.com", Role.MEI);
		criar("Ana", "ana.409@contabil.com", Role.ACCOUNTANT);
		conceder(mei, "ana.409@contabil.com");

		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"ana.409@contabil.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNTANT_ACCESS_ALREADY_GRANTED"));
	}

	@Test
	void deveValidarOEmailInformado() throws Exception {
		User mei = criar("Maria", "maria.valida@exemplo.com", Role.MEI);

		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"nao-e-email\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.accountantEmail").exists());

		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.accountantEmail").exists());
	}

	@Test
	void naoDeveRevogarAcessoDeOutroMeiNemRevogarDuasVezes() throws Exception {
		User maria = criar("Maria", "maria.dona@exemplo.com", Role.MEI);
		User joao = criar("João", "joao.intruso@exemplo.com", Role.MEI);
		criar("Ana", "ana.dona@contabil.com", Role.ACCOUNTANT);
		String id = conceder(maria, "ana.dona@contabil.com");

		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(joao)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete(PATH + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(maria)))
				.andExpect(status().isNotFound());
	}

	@Test
	void listaMostraSomenteOsAcessosDoProprioMei() throws Exception {
		User maria = criar("Maria", "maria.lista@exemplo.com", Role.MEI);
		User joao = criar("João", "joao.lista@exemplo.com", Role.MEI);
		criar("Ana", "ana.lista@contabil.com", Role.ACCOUNTANT);
		conceder(maria, "ana.lista@contabil.com");

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(joao)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void contadorNaoPodeConcederListarNemRevogarAcesso() throws Exception {
		User contador = criar("Ana", "ana.proibida@contabil.com", Role.ACCOUNTANT);
		criar("Bia", "bia.proibida@contabil.com", Role.ACCOUNTANT);

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(contador))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"bia.proibida@contabil.com\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete(PATH + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(contador)))
				.andExpect(status().isForbidden());
	}

	@Test
	void deveExigirAutenticacao() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(PATH + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	private String conceder(User mei, String emailContador) throws Exception {
		String corpo = mockMvc.perform(post(PATH)
						.header(HttpHeaders.AUTHORIZATION, bearer(mei))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountantEmail\": \"" + emailContador + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$.id");
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
