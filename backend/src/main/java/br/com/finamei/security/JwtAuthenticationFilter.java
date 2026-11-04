package br.com.finamei.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
			authenticate(request, header.substring(BEARER_PREFIX.length()));
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, String token) {
		try {
			Claims claims = jwtService.parseClaims(token);
			UUID userId = UUID.fromString(claims.getSubject());
			// Conta desativada por um administrador perde o acesso na hora, mesmo com token ainda válido.
			if (userRepository.existsByIdAndActiveFalse(userId)) {
				SecurityContextHolder.clearContext();
				return;
			}
			String role = claims.get(JwtService.ROLE_CLAIM, String.class);

			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
			var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (JwtException | IllegalArgumentException ex) {
			SecurityContextHolder.clearContext();
		}
	}
}
