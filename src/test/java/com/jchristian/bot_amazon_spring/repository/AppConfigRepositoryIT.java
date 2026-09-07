package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppConfigRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private AppConfigRepository appConfigRepository;

	@Test
	void salvaEBuscaPorChaveExistente() {
		AppConfig config = AppConfig.builder().chave("teste.chave-existente").valor("123").build();
		appConfigRepository.saveAndFlush(config);

		Optional<AppConfig> encontrado = appConfigRepository.findByChave("teste.chave-existente");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getValor()).isEqualTo("123");
	}

	@Test
	void buscaPorChaveInexistenteRetornaVazio() {
		Optional<AppConfig> encontrado = appConfigRepository.findByChave("chave-que-nao-existe");

		assertThat(encontrado).isEmpty();
	}

	@Test
	void inserirChaveDuplicadaViolaConstraintUnica() {
		appConfigRepository.saveAndFlush(AppConfig.builder().chave("teste.duplicada").valor("1").build());

		AppConfig duplicado = AppConfig.builder().chave("teste.duplicada").valor("2").build();

		assertThrows(DataIntegrityViolationException.class, () -> appConfigRepository.saveAndFlush(duplicado));
	}

}
