package com.jchristian.bot_amazon_spring.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class RequiredEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

	static final String DISABLE_PROPERTY = "bot-amazon.required-env-validator.disabled";

	private static final List<String> PROPRIEDADES_OBRIGATORIAS = List.of(
			"spring.datasource.url",
			"spring.datasource.username",
			"spring.datasource.password",
			"telegram.bot.token",
			"evolution.api.url",
			"evolution.api.key",
			"evolution.api.instance",
			"openrouter.api-key",
			"openrouter.model",
			"afiliado.tag");

	@Override
	public int getOrder() {
		return ConfigDataEnvironmentPostProcessor.ORDER + 1;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (environment.getProperty(DISABLE_PROPERTY, Boolean.class, false)) {
			return;
		}

		List<String> ausentes = new ArrayList<>();
		for (String propriedade : PROPRIEDADES_OBRIGATORIAS) {
			if (!StringUtils.hasText(environment.getProperty(propriedade))) {
				ausentes.add(propriedade);
			}
		}

		if (!ausentes.isEmpty()) {
			throw new IllegalStateException(
					"Variaveis de ambiente obrigatorias ausentes ou vazias: " + String.join(", ", ausentes));
		}
	}

}
