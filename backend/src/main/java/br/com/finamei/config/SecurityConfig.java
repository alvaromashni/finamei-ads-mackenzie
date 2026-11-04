package br.com.finamei.config;

import br.com.finamei.security.JwtAuthenticationFilter;
import br.com.finamei.security.JwtService;
import br.com.finamei.security.RestAccessDeniedHandler;
import br.com.finamei.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private static final int BCRYPT_STRENGTH = 10;

	/** Dados financeiros do MEI: lançamentos, categorias e guias DAS. */
	private static final String[] FINANCIAL_PATHS = {
			"/api/v1/transactions/**", "/api/v1/categories/**", "/api/v1/das/**"
	};

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Sem isto o navegador esconde o nome do arquivo exportado (OF16) do frontend em outra origem.
		configuration.setExposedHeaders(List.of("Content-Disposition"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtService jwtService,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/error",
								"/actuator/health",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/api/v1/auth/**")
						.permitAll()
						// RN11: só o MEI concede, lista e revoga acessos de contador.
						.requestMatchers("/api/v1/accountant-access/**").hasRole("MEI")
						// OF17: área do contador só para ACCOUNTANT e somente leitura (nenhum método de escrita).
						.requestMatchers(HttpMethod.GET, "/api/v1/accountant/**").hasRole("ACCOUNTANT")
						.requestMatchers("/api/v1/accountant/**").denyAll()
						// ONF05: contador é somente leitura, nunca grava dados financeiros.
						.requestMatchers(HttpMethod.POST, FINANCIAL_PATHS).hasAnyRole("MEI", "ADMIN")
						.requestMatchers(HttpMethod.PUT, FINANCIAL_PATHS).hasAnyRole("MEI", "ADMIN")
						.requestMatchers(HttpMethod.PATCH, FINANCIAL_PATHS).hasAnyRole("MEI", "ADMIN")
						.requestMatchers(HttpMethod.DELETE, FINANCIAL_PATHS).hasAnyRole("MEI", "ADMIN")
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
