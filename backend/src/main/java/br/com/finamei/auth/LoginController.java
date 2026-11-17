package br.com.finamei.auth;

import br.com.finamei.auth.dto.LoginRequest;
import br.com.finamei.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

	private final LoginService loginService;

	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(loginService.login(request));
	}

	/**
	 * Mantém a sessão viva enquanto há uso (ONF04). O filtro renova o JWT quando o token
	 * atual já tem mais de um minuto; esta rota só existe para o frontend avisar atividade.
	 */
	@PostMapping("/session")
	public ResponseEntity<Void> touchSession() {
		return ResponseEntity.noContent().build();
	}
}
