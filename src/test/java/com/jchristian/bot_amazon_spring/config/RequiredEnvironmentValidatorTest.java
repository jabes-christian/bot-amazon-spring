package com.jchristian.bot_amazon_spring.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEnvironmentValidatorTest {

	private final RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator();

	private MockEnvironment ambienteComTodasAsVariaveisObrigatorias() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/bot_amazon");
		environment.setProperty("spring.datasource.username", "postgres");
		environment.setProperty("spring.datasource.password", "postgres");
		environment.setProperty("telegram.bot.token", "token-telegram");
		environment.setProperty("evolution.api.url", "https://evolution.example.com");
		environment.setProperty("evolution.api.key", "chave-evolution");
		environment.setProperty("evolution.api.instance", "instancia-1");
		environment.setProperty("openrouter.api-key", "chave-openrouter");
		environment.setProperty("openrouter.model", "modelo-openrouter");
		environment.setProperty("afiliado.tag", "tag-20");
		return environment;
	}

	@Test
	void falhaCitandoNomeDaVariavelQuandoUmaVariavelObrigatoriaEstaAusente() {
		MockEnvironment environment = ambienteComTodasAsVariaveisObrigatorias();
		environment.setProperty("afiliado.tag", "");

		assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("afiliado.tag");
	}

	@Test
	void falhaCitandoTodosOsNomesQuandoMultiplasVariaveisObrigatoriasEstaoAusentes() {
		MockEnvironment environment = ambienteComTodasAsVariaveisObrigatorias();
		environment.setProperty("telegram.bot.token", "");
		environment.setProperty("evolution.api.key", "");
		environment.setProperty("openrouter.model", "");

		assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("telegram.bot.token")
				.hasMessageContaining("evolution.api.key")
				.hasMessageContaining("openrouter.model");
	}

	@Test
	void naoLancaExcecaoQuandoTodasAsVariaveisObrigatoriasEstaoPresentes() {
		MockEnvironment environment = ambienteComTodasAsVariaveisObrigatorias();

		assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
	}

	@Test
	void naoValidaQuandoPropriedadeDeDesativacaoEstaPresente() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(RequiredEnvironmentValidator.DISABLE_PROPERTY, "true");

		assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
	}

}
