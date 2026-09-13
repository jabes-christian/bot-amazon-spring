package com.jchristian.bot_amazon_spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BotAmazonSpringApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Environment environment;

	@Test
	void contextLoadsAndMigrationApplies() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
						+ "AND table_name IN ('categoria_coleta', 'product', 'price_history', 'app_config', "
						+ "'channel', 'dispatch_history')",
				Integer.class);
		assertThat(tableCount).isEqualTo(6);

		Integer categoriasSeed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categoria_coleta", Integer.class);
		assertThat(categoriasSeed).isEqualTo(5);

		Integer appConfigSeed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_config", Integer.class);
		assertThat(appConfigSeed).isEqualTo(10);

		String percentualMinimoQueda = jdbcTemplate.queryForObject(
				"SELECT valor FROM app_config WHERE chave = 'coleta.percentual-minimo-queda'", String.class);
		assertThat(percentualMinimoQueda).isEqualTo("10");

		String llmTimeoutSegundos = jdbcTemplate.queryForObject(
				"SELECT valor FROM app_config WHERE chave = 'enriquecimento.llm-timeout-segundos'", String.class);
		assertThat(llmTimeoutSegundos).isEqualTo("15");
	}

	@Test
	void flywaySchemaHistoryTemAsTresMigrationsAplicadasComSucessoEDdlAutoEhValidate() {
		List<Map<String, Object>> historico = jdbcTemplate.queryForList(
				"SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

		assertThat(historico).extracting(row -> row.get("version")).containsExactly("1", "2", "3");
		assertThat(historico).allMatch(row -> Boolean.TRUE.equals(row.get("success")));

		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
	}

}
