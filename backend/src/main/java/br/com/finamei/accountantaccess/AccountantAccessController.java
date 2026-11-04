package br.com.finamei.accountantaccess;

import br.com.finamei.accountantaccess.dto.AccountantAccessResponse;
import br.com.finamei.accountantaccess.dto.GrantAccountantAccessRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Somente MEI (regra em SecurityConfig). O MEI é sempre o usuário do token. */
@RestController
@RequestMapping("/api/v1/accountant-access")
public class AccountantAccessController {

	private final AccountantAccessService service;

	public AccountantAccessController(AccountantAccessService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<List<AccountantAccessResponse>> list(@AuthenticationPrincipal UUID meiId) {
		return ResponseEntity.ok(service.listActive(meiId));
	}

	@PostMapping
	public ResponseEntity<AccountantAccessResponse> grant(
			@AuthenticationPrincipal UUID meiId,
			@Valid @RequestBody GrantAccountantAccessRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.grant(meiId, request.accountantEmail()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> revoke(@AuthenticationPrincipal UUID meiId, @PathVariable UUID id) {
		service.revoke(meiId, id);
		return ResponseEntity.noContent().build();
	}
}
