package com.jchristian.bot_amazon_spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BotAmazonSpringApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsAndMigrationApplies() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
						+ "AND table_name IN ('categoria_coleta', 'product', 'price_history', 'app_config')",
				Integer.class);
		assertThat(tableCount).isEqualTo(4);

		Integer categoriasSeed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categoria_coleta", Integer.class);
		assertThat(categoriasSeed).isEqualTo(5);

		Integer appConfigSeed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_config", Integer.class);
		assertThat(appConfigSeed).isEqualTo(4);
	}

}
