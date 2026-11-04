package br.com.finamei.admin;

import br.com.finamei.admin.dto.AdminUserResponse;
import br.com.finamei.admin.dto.UpdateUserStatusRequest;
import br.com.finamei.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Somente ADMIN (regra em SecurityConfig). O administrador vem sempre do token. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

	private final AdminUserService service;

	public AdminUserController(AdminUserService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<PageResponse<AdminUserResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(service.list(page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminUserResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(service.get(id));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AdminUserResponse> updateStatus(
			@AuthenticationPrincipal UUID adminId,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserStatusRequest request) {
		return ResponseEntity.ok(service.updateStatus(adminId, id, request.active()));
	}

	@PostMapping("/{id}/login-unlock")
	public ResponseEntity<AdminUserResponse> unlockLogin(@AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
		return ResponseEntity.ok(service.unlockLogin(adminId, id));
	}
}
