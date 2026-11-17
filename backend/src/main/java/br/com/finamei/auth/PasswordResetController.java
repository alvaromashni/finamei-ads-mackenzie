package br.com.finamei.auth;

import br.com.finamei.auth.dto.ConfirmPasswordResetRequest;
import br.com.finamei.auth.dto.PasswordResetMessageResponse;
import br.com.finamei.auth.dto.RequestPasswordResetRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recuperação de senha (UC-002, RN10). Rotas públicas, sob /api/v1/auth. */
@RestController
@RequestMapping("/api/v1/auth/password-resets")
public class PasswordResetController {

	private final PasswordResetService service;

	public PasswordResetController(PasswordResetService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<PasswordResetMessageResponse> request(@Valid @RequestBody RequestPasswordResetRequest request) {
		return ResponseEntity.ok(new PasswordResetMessageResponse(service.requestReset(request.email())));
	}

	@PostMapping("/confirm")
	public ResponseEntity<PasswordResetMessageResponse> confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
		return ResponseEntity.ok(new PasswordResetMessageResponse(service.confirm(request.token(), request.password())));
	}
}
