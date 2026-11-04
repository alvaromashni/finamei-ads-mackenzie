package br.com.finamei.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * ONF03: o perfil de produção não pode ter valor padrão inseguro para segredos nem origem
 * CORS sem HTTPS, e nenhum perfil liga log detalhado de SQL ou de segurança.
 */
class ProductionConfigurationTest {

	private static Properties prod;
	private static Properties base;

	@BeforeAll
	static void carregarConfiguracoes() {
		prod = carregar("application-prod.yml");
		base = carregar("application.yml");
	}

	@Test
	void segredoJwtEmProducaoNaoTemValorPadrao() {
		assertThat(prod.getProperty("app.jwt.secret")).isEqualTo("${JWT_SECRET}");
	}

	@Test
	void credenciaisDoBancoEmProducaoNaoTemValorPadrao() {
		assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
		assertThat(prod.getProperty("spring.datasource.username")).isEqualTo("${DB_USER}");
		assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
	}

	@Test
	void corsDeProducaoAceitaSomenteOrigemHttps() {
		String origens = prod.getProperty("app.cors.allowed-origins");

		assertThat(origens).startsWith("${CORS_ALLOWED_ORIGINS:https://");
		assertThat(origens).doesNotContain("http://").doesNotContain("*");
	}

	@Test
	void producaoReconheceHttpsInformadoPeloProxyDoRender() {
		assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
	}

	@Test
	void segredoPadraoDeDesenvolvimentoEstaIdentificadoComoInseguro() {
		assertThat(base.getProperty("app.jwt.secret")).contains("do-not-use-in-production");
	}

	@Test
	void nenhumPerfilLigaLogDeSqlOuAjustaNiveisDeLog() {
		for (Properties config : new Properties[] {base, prod}) {
			assertThat(config.getProperty("spring.jpa.show-sql")).isNull();
			assertThat(config.stringPropertyNames()).noneMatch(chave -> chave.startsWith("logging.level"));
		}
	}

	@Test
	void expiracaoDoTokenPadraoEhDe30Minutos() {
		assertThat(base.getProperty("app.jwt.expiration-minutes")).isEqualTo("${JWT_EXPIRATION_MINUTES:30}");
	}

	private static Properties carregar(String arquivo) {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource(arquivo));
		return yaml.getObject();
	}
}
