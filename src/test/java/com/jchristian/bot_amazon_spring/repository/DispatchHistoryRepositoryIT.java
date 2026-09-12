package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.DispatchHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DispatchHistoryRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private DispatchHistoryRepository dispatchHistoryRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CategoriaColetaRepository categoriaColetaRepository;

	@Autowired
	private ChannelRepository channelRepository;

	private Product product;

	private Channel channel;

	@BeforeEach
	void setUp() {
		CategoriaColeta categoria = categoriaColetaRepository.findByAtivoTrue().get(0);
		product = productRepository.saveAndFlush(Product.builder()
				.asin("B0DISPATCH1")
				.categoria(categoria)
				.titulo("Produto para dedup de disparo")
				.precoAtual(new BigDecimal("100.00"))
				.build());
		channel = channelRepository.saveAndFlush(Channel.builder()
				.tipo(TipoCanal.TELEGRAM)
				.identificador("-100999")
				.categoriasAceitas(null)
				.ativo(true)
				.build());
	}

	@Test
	void existsByProductAndChannelAndEnviadoEmAfterRetornaTrueDentroDaJanela() {
		DispatchHistory registro =
				dispatchHistoryRepository.saveAndFlush(DispatchHistory.builder().product(product).channel(channel).build());
		assertThat(registro.getEnviadoEm()).isNotNull();

		boolean existeDentroDaJanela = dispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(
				product, channel, LocalDateTime.now().minusDays(7));

		assertThat(existeDentroDaJanela).isTrue();
	}

	@Test
	void existsByProductAndChannelAndEnviadoEmAfterRetornaFalseForaDaJanela() {
		DispatchHistory registro =
				dispatchHistoryRepository.saveAndFlush(DispatchHistory.builder().product(product).channel(channel).build());
		registro.setEnviadoEm(LocalDateTime.now().minusDays(10));
		dispatchHistoryRepository.saveAndFlush(registro);

		boolean existeDentroDaJanela = dispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(
				product, channel, LocalDateTime.now().minusDays(7));

		assertThat(existeDentroDaJanela).isFalse();
	}

}
