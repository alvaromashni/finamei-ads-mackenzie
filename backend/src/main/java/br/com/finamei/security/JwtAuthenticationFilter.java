package br.com.finamei.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.finamei.user.User;
import br.com.finamei.user.UserRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String RENEWED_TOKEN_HEADER = "X-Renewed-Token";

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(AUTHORIZATION_HEADER);

		if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(request, response, header.substring(BEARER_PREFIX.length()));
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, HttpServletResponse response, String token) {
		try {
			Claims claims = jwtService.parseClaims(token);
			UUID userId = UUID.fromString(claims.getSubject());
			User user = userRepository.findById(userId).orElse(null);
			// Conta desativada perde o acesso na hora, mesmo com token ainda válido.
			if (user == null || !user.isActive() || sessionVersionDiffers(claims, user)) {
				SecurityContextHolder.clearContext();
				return;
			}
			String role = claims.get(JwtService.ROLE_CLAIM, String.class);

			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
			var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			// Cada uso depois do primeiro minuto empurra o fim da sessão (ONF04).
			if (jwtService.shouldRenew(claims)) {
				response.setHeader(RENEWED_TOKEN_HEADER, jwtService.renew(claims));
			}
		} catch (JwtException | IllegalArgumentException ex) {
			SecurityContextHolder.clearContext();
		}
	}

	/** Token sem o claim conta como versão 0, a das contas que ainda não trocaram a senha. */
	private static boolean sessionVersionDiffers(Claims claims, User user) {
		Integer version = claims.get(JwtService.SESSION_VERSION_CLAIM, Integer.class);
		return (version == null ? 0 : version) != user.getSessionVersion();
	}
}
