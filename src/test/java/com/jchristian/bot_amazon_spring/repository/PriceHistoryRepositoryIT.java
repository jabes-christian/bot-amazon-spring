package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.PriceHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PriceHistoryRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CategoriaColetaRepository categoriaColetaRepository;

	private Product product;

	@BeforeEach
	void setUp() {
		CategoriaColeta categoria = categoriaColetaRepository.findByAtivoTrue().get(0);
		product = productRepository.saveAndFlush(Product.builder()
				.asin("B0HISTORICO1")
				.categoria(categoria)
				.titulo("Produto com historico")
				.precoAtual(new BigDecimal("100.00"))
				.build());
	}

	@Test
	void duasColetasGeramDuasEntradasDeHistoricoSemDeduplicar() {
		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("100.00")).build());
		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("90.00")).build());

		long total = priceHistoryRepository.countByProduct(product);

		assertThat(total).isEqualTo(2);
	}

	@Test
	void findMenorPrecoByProductRetornaOMinimoEntreAsEntradas() {
		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("100.00")).build());
		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("79.90")).build());
		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("85.00")).build());

		Optional<BigDecimal> menorPreco = priceHistoryRepository.findMenorPrecoByProduct(product);

		assertThat(menorPreco).isPresent();
		assertThat(menorPreco.get()).isEqualByComparingTo("79.90");
	}

	@Test
	void findMenorPrecoDesdeIgnoraEntradaMaisAntigaQueAJanela() {
		PriceHistory antiga = priceHistoryRepository.saveAndFlush(
				PriceHistory.builder().product(product).preco(new BigDecimal("50.00")).build());
		antiga.setCapturadoEm(LocalDateTime.now().minusDays(200));
		priceHistoryRepository.saveAndFlush(antiga);

		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("90.00")).build());

		Optional<BigDecimal> menorPrecoDesde =
				priceHistoryRepository.findMenorPrecoDesde(product, LocalDateTime.now().minusDays(90));

		assertThat(menorPrecoDesde).isPresent();
		assertThat(menorPrecoDesde.get()).isEqualByComparingTo("90.00");
	}

	@Test
	void findMenorPrecoDesdeConsideraEntradaDentroDaJanelaComPrecoMenor() {
		PriceHistory antiga = priceHistoryRepository.saveAndFlush(
				PriceHistory.builder().product(product).preco(new BigDecimal("95.00")).build());
		antiga.setCapturadoEm(LocalDateTime.now().minusDays(200));
		priceHistoryRepository.saveAndFlush(antiga);

		priceHistoryRepository.saveAndFlush(PriceHistory.builder().product(product).preco(new BigDecimal("60.00")).build());

		Optional<BigDecimal> menorPrecoDesde =
				priceHistoryRepository.findMenorPrecoDesde(product, LocalDateTime.now().minusDays(90));

		assertThat(menorPrecoDesde).isPresent();
		assertThat(menorPrecoDesde.get()).isEqualByComparingTo("60.00");
	}

}
