package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CategoriaColetaRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private CategoriaColetaRepository categoriaColetaRepository;

	@Test
	void findByAtivoTrueRetornaAsCategoriasSeedadas() {
		List<CategoriaColeta> ativas = categoriaColetaRepository.findByAtivoTrue();

		assertThat(ativas).extracting(CategoriaColeta::getCodigo)
				.containsExactlyInAnyOrder("MONITOR", "NOTEBOOK", "PERIFERICO", "CADEIRA_GAMER", "MESA");
	}

	@Test
	void categoriaInativaNaoApareceEmFindByAtivoTrue() {
		categoriaColetaRepository.saveAndFlush(CategoriaColeta.builder()
				.codigo("TESTE_INATIVA")
				.keywordBusca("qualquer coisa")
				.ativo(false)
				.build());

		List<CategoriaColeta> ativas = categoriaColetaRepository.findByAtivoTrue();

		assertThat(ativas).extracting(CategoriaColeta::getCodigo).doesNotContain("TESTE_INATIVA");
	}

}
