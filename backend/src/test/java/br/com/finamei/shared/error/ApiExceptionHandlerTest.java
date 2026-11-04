package br.com.finamei.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/** Contrato de erro da API: status, código estável e nenhuma mensagem interna na resposta. */
class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void autenticacaoFalhaResponde401SemDetalhesDaExcecao() {
		ResponseEntity<ErrorResponse> resposta =
				handler.handleAuthenticationException(new BadCredentialsException("detalhe interno"));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(resposta.getBody().code()).isEqualTo("UNAUTHENTICATED");
		assertThat(resposta.getBody().message()).doesNotContain("detalhe interno");
	}

	@Test
	void acessoNegadoResponde403SemDetalhesDaExcecao() {
		ResponseEntity<ErrorResponse> resposta = handler.handleAccessDenied(new AccessDeniedException("interno"));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(resposta.getBody().code()).isEqualTo("ACCESS_DENIED");
		assertThat(resposta.getBody().message()).doesNotContain("interno");
	}

	@Test
	void contaBloqueadaResponde423ComRetryAfter() {
		ResponseEntity<ErrorResponse> resposta =
				handler.handleAccountLocked(new AccountLockedException(Duration.ofMinutes(15)));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
		assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
		assertThat(resposta.getBody().code()).isEqualTo("ACCOUNT_LOCKED");
	}

	@Test
	void conflitosDeNegocioRespondem409ComCodigoProprio() {
		assertThat(handler.handleEmailAlreadyRegistered(new EmailAlreadyRegisteredException()).getBody().code())
				.isEqualTo("EMAIL_ALREADY_REGISTERED");
		assertThat(handler.handleCategoryAlreadyExists(new CategoryAlreadyExistsException()).getBody().code())
				.isEqualTo("CATEGORY_ALREADY_EXISTS");
		assertThat(handler.handleDasAlreadyPaid(new DasAlreadyPaidException()).getBody().code())
				.isEqualTo("DAS_ALREADY_PAID");
		assertThat(handler.handleDasNotPaid(new DasNotPaidException()).getBody().code())
				.isEqualTo("DAS_NOT_PAID");
		assertThat(handler.handleAccountantAccessAlreadyGranted(
				new AccountantAccessAlreadyGrantedException()).getBody().code())
				.isEqualTo("ACCOUNTANT_ACCESS_ALREADY_GRANTED");
		assertThat(handler.handleDasNotPaid(new DasNotPaidException()).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void violacaoDeIntegridadeResponde409SemExporMensagemDoBanco() {
		ResponseEntity<ErrorResponse> resposta = handler.handleDataIntegrityViolation(
				new DataIntegrityViolationException("constraint uk_users_email violada"));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(resposta.getBody().code()).isEqualTo("DATA_CONFLICT");
		assertThat(resposta.getBody().message()).doesNotContain("uk_users_email");
	}

	@Test
	void recursoInexistenteResponde404() {
		ResponseEntity<ErrorResponse> resposta =
				handler.handleResourceNotFound(new ResourceNotFoundException("Lançamento não encontrado."));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resposta.getBody().message()).isEqualTo("Lançamento não encontrado.");
	}

	@Test
	void validacaoDeCampoResponde400ComOCampo() {
		ResponseEntity<ErrorResponse> resposta =
				handler.handleFieldValidation(new FieldValidationException("categoryId", "Selecione uma categoria."));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody().code()).isEqualTo("VALIDATION_ERROR");
		assertThat(resposta.getBody().fieldErrors()).containsEntry("categoryId", "Selecione uma categoria.");
	}

	@Test
	void parametroObrigatorioAusenteResponde400ComONomeDoParametro() {
		ResponseEntity<ErrorResponse> resposta =
				handler.handleMissingParameter(new MissingServletRequestParameterException("month", "String"));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resposta.getBody().fieldErrors()).containsKey("month");
	}
}
