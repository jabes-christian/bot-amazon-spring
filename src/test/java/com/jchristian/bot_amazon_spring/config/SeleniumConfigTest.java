package com.jchristian.bot_amazon_spring.config;

import com.jchristian.bot_amazon_spring.scraper.AmazonProductScraper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SeleniumConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(SeleniumConfig.class, AmazonSelectorsProperties.class, AmazonProductScraper.class);

	@Test
	void webDriverNaoEInstanciadoNoRefreshDoContextoSemUsoExplicito() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeanFactory().containsSingleton("webDriver")).isFalse();
		});
	}

}
