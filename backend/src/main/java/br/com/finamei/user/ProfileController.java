package br.com.finamei.user;

import br.com.finamei.auth.dto.UserResponse;
import br.com.finamei.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do usuário autenticado (OF04). O usuário vem sempre do JWT, nunca da URL. */
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	public ResponseEntity<UserResponse> get(@AuthenticationPrincipal UUID userId) {
		return ResponseEntity.ok(UserResponse.from(profileService.getProfile(userId)));
	}

	@PutMapping
	public ResponseEntity<UserResponse> update(
			@AuthenticationPrincipal UUID userId,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(UserResponse.from(profileService.updateProfile(userId, request)));
	}
}
