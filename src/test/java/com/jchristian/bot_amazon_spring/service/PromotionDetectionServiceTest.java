package com.jchristian.bot_amazon_spring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import com.jchristian.bot_amazon_spring.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionDetectionServiceTest {

	private static final String CHAVE_PERCENTUAL_MINIMO = "coleta.percentual-minimo-queda";

	@Mock
	private ProductRepository productRepository;

	@Mock
	private PriceHistoryRepository priceHistoryRepository;

	@Mock
	private ConfigService configService;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(PromotionDetectionService.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(PromotionDetectionService.class);
		logger.detachAppender(logAppender);
	}

	private PromotionDetectionService novoService() {
		return new PromotionDetectionService(productRepository, priceHistoryRepository, configService);
	}

	private static Product produto(Long id, BigDecimal precoAtual, BigDecimal precoRiscado, BigDecimal lastCandidatoPreco) {
		return Product.builder()
				.id(id)
				.asin("B0" + id)
				.titulo("Produto " + id)
				.precoAtual(precoAtual)
				.precoRiscado(precoRiscado)
				.lastCandidatoPreco(lastCandidatoPreco)
				.build();
	}

	@Test
	void produtoComHistoricoSuficienteUsaMenorPrecoDoHistoricoComoBase() {
		Product produto = produto(1L, new BigDecimal("80.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(3L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).hasSize(1);
		assertThat(candidatos.get(0).percentualDesconto()).isEqualByComparingTo("20.00");
		assertThat(candidatos.get(0).precoBase()).isEqualByComparingTo("100.00");
	}

	@Test
	void percentualMinimoConfiguravelGovernaOLimiarDeDeteccao() {
		Product produto = produto(2L, new BigDecimal("85.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(2L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("20"));

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).isEmpty();
	}

	@Test
	void produtoSinalizadoRetornaCandidatoComPercentualEAtualizaLastCandidatoPreco() {
		Product produto = produto(3L, new BigDecimal("70.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(2L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).hasSize(1);
		assertThat(candidatos.get(0).produto()).isSameAs(produto);
		assertThat(candidatos.get(0).percentualDesconto()).isEqualByComparingTo("30.00");
		verify(productRepository)
				.save(argThat(p -> ((Product) p).getLastCandidatoPreco().compareTo(new BigDecimal("70.00")) == 0));
	}

	@Test
	void produtoJaSinalizadoNoMesmoPrecoNaoESinalizadoNovamente() {
		Product produto = produto(4L, new BigDecimal("70.00"), null, new BigDecimal("70.00"));
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(2L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).isEmpty();
		verify(productRepository, never()).save(any());
	}

	@Test
	void coldStartComPrecoRiscadoUsaPrecoRiscadoComoBase() {
		Product produto = produto(5L, new BigDecimal("80.00"), new BigDecimal("100.00"), null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(1L);
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).hasSize(1);
		assertThat(candidatos.get(0).percentualDesconto()).isEqualByComparingTo("20.00");
		assertThat(candidatos.get(0).precoBase()).isEqualByComparingTo("100.00");
		verify(priceHistoryRepository, never()).findMenorPrecoByProduct(any());
	}

	@Test
	void coldStartSemPrecoRiscadoNaoESinalizado() {
		Product produto = produto(6L, new BigDecimal("80.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(0L);

		List<CandidatoPromocaoDTO> candidatos = novoService().buscarCandidatosElegiveis();

		assertThat(candidatos).isEmpty();
		verify(productRepository, never()).save(any());
	}

	@Test
	void produtoElegivelLogaDecisaoDeElegibilidadePrefixadaDeteccao() {
		Product produto = produto(7L, new BigDecimal("70.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(2L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		novoService().buscarCandidatosElegiveis();

		assertThat(logAppender.list).anyMatch(evento -> evento.getFormattedMessage().startsWith("DETECCAO:")
				&& evento.getFormattedMessage().contains("asin=" + produto.getAsin())
				&& evento.getFormattedMessage().contains("elegivel=true"));
	}

	@Test
	void produtoNaoElegivelLogaDecisaoDeElegibilidadePrefixadaDeteccao() {
		Product produto = produto(8L, new BigDecimal("95.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(2L);
		when(priceHistoryRepository.findMenorPrecoByProduct(produto)).thenReturn(Optional.of(new BigDecimal("100.00")));
		when(configService.getBigDecimal(eq(CHAVE_PERCENTUAL_MINIMO), any())).thenReturn(new BigDecimal("10"));

		novoService().buscarCandidatosElegiveis();

		assertThat(logAppender.list).anyMatch(evento -> evento.getFormattedMessage().startsWith("DETECCAO:")
				&& evento.getFormattedMessage().contains("asin=" + produto.getAsin())
				&& evento.getFormattedMessage().contains("elegivel=false"));
	}

	@Test
	void produtoSemBaseDePrecoLogaAvaliacaoComPrefixoDeteccao() {
		Product produto = produto(9L, new BigDecimal("80.00"), null, null);
		when(productRepository.findAll()).thenReturn(List.of(produto));
		when(priceHistoryRepository.countByProduct(produto)).thenReturn(0L);

		novoService().buscarCandidatosElegiveis();

		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.INFO
				&& evento.getFormattedMessage().startsWith("DETECCAO:")
				&& evento.getFormattedMessage().contains("asin=" + produto.getAsin()));
	}

}
