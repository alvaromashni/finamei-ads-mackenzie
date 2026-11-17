package br.com.finamei.security;

import br.com.finamei.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

	static final String ROLE_CLAIM = "role";
	static final String EMAIL_CLAIM = "email";
	static final String SESSION_VERSION_CLAIM = "ver";

	private final SecretKey key;
	private final Duration expiration;

	public JwtService(
			@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(expirationMinutes);
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(user.getId().toString())
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(ROLE_CLAIM, user.getRole().name())
				.claim(SESSION_VERSION_CLAIM, user.getSessionVersion())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	public long getExpirationSeconds() {
		return expiration.toSeconds();
	}

	/** Novo token com os mesmos dados e validade contada a partir de agora (ONF04). */
	public String renew(Claims claims) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(claims.getSubject())
				.claim(EMAIL_CLAIM, claims.get(EMAIL_CLAIM, String.class))
				.claim(ROLE_CLAIM, claims.get(ROLE_CLAIM, String.class))
				.claim(SESSION_VERSION_CLAIM, claims.get(SESSION_VERSION_CLAIM, Integer.class))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	/** Renova só depois do primeiro minuto, para não trocar o token a cada requisição. */
	public boolean shouldRenew(Claims claims) {
		Date issuedAt = claims.getIssuedAt();
		if (issuedAt == null) {
			return false;
		}
		return Instant.now().isAfter(issuedAt.toInstant().plusSeconds(60));
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
