package com.jchristian.bot_amazon_spring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.dto.ScrapedProductDTO;
import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.PriceHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.CategoriaColetaRepository;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import com.jchristian.bot_amazon_spring.repository.ProductRepository;
import com.jchristian.bot_amazon_spring.scraper.AmazonProductScraper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColetaServiceTest {

	private static final String CHAVE_PRECO_MINIMO = "coleta.preco-minimo-valido";
	private static final String CHAVE_PRECO_MAXIMO = "coleta.preco-maximo-valido";
	private static final String CHAVE_INTERVALO_CATEGORIAS = "coleta.intervalo-entre-categorias-segundos";

	@Mock
	private AmazonProductScraper amazonProductScraper;

	@Mock
	private CategoriaColetaRepository categoriaColetaRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private PriceHistoryRepository priceHistoryRepository;

	@Mock
	private ConfigService configService;

	private ColetaService novoService() {
		return new ColetaService(amazonProductScraper, categoriaColetaRepository, productRepository,
				priceHistoryRepository, configService);
	}

	private static CategoriaColeta categoria(String codigo, String keyword) {
		return CategoriaColeta.builder().id(1L).codigo(codigo).keywordBusca(keyword).ativo(true).build();
	}

	private static ScrapedProductDTO scraped(String asin, String titulo, String preco) {
		return ScrapedProductDTO.of(asin, titulo, new BigDecimal(preco), null, "https://img.example/x.jpg",
				"https://amazon.com.br/dp/" + asin);
	}

	private void stubLimiaresDePreco() {
		when(configService.getBigDecimal(eq(CHAVE_PRECO_MINIMO), any())).thenReturn(new BigDecimal("0.01"));
		when(configService.getBigDecimal(eq(CHAVE_PRECO_MAXIMO), any())).thenReturn(new BigDecimal("50000.00"));
	}

	@Test
	void chamaScraperComAKeywordDaCategoriaAtiva() {
		when(categoriaColetaRepository.findByAtivoTrue()).thenReturn(List.of(categoria("MONITOR", "monitor gamer")));
		when(amazonProductScraper.buscarPorKeyword("monitor gamer")).thenReturn(List.of());

		novoService().executarCicloColeta();

		verify(amazonProductScraper).buscarPorKeyword("monitor gamer");
	}

	@Test
	void categoriaSemResultadoNaoInterrompeCicloESeguiParaProxima() {
		when(categoriaColetaRepository.findByAtivoTrue())
				.thenReturn(List.of(categoria("MONITOR", "monitor"), categoria("NOTEBOOK", "notebook")));
		when(amazonProductScraper.buscarPorKeyword("monitor")).thenReturn(List.of());
		when(amazonProductScraper.buscarPorKeyword("notebook")).thenReturn(List.of(scraped("B0X", "Notebook X", "1000.00")));
		when(configService.getLong(eq(CHAVE_INTERVALO_CATEGORIAS), anyLong())).thenReturn(0L);
		when(productRepository.findByAsin("B0X")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubLimiaresDePreco();

		novoService().executarCicloColeta();

		verify(productRepository).save(argThat(p -> ((Product) p).getAsin().equals("B0X")));
	}

	@Test
	void precoForaDaFaixaDeSanidadeDescartaProdutoEContinua() {
		when(categoriaColetaRepository.findByAtivoTrue()).thenReturn(List.of(categoria("MONITOR", "monitor")));
		when(amazonProductScraper.buscarPorKeyword("monitor"))
				.thenReturn(List.of(scraped("B0VALIDO", "Valido", "500.00"), scraped("B0INVALIDO", "Invalido", "60000.00")));
		when(productRepository.findByAsin("B0VALIDO")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubLimiaresDePreco();

		Logger logger = (Logger) LoggerFactory.getLogger(ColetaService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			novoService().executarCicloColeta();

			verify(productRepository, times(1)).save(any());
			verify(productRepository).save(argThat(p -> ((Product) p).getAsin().equals("B0VALIDO")));
			verify(productRepository, never()).findByAsin("B0INVALIDO");
			assertThat(appender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
					&& evento.getFormattedMessage().contains("B0INVALIDO")
					&& evento.getFormattedMessage().contains("60000.00"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void produtoComAsinExistenteEAtualizadoNaoDuplicado() {
		Product existente = Product.builder()
				.id(42L)
				.asin("B0X")
				.categoria(categoria("MONITOR", "monitor"))
				.titulo("Titulo Antigo")
				.precoAtual(new BigDecimal("100.00"))
				.build();
		when(categoriaColetaRepository.findByAtivoTrue()).thenReturn(List.of(categoria("MONITOR", "monitor")));
		when(amazonProductScraper.buscarPorKeyword("monitor")).thenReturn(List.of(scraped("B0X", "Titulo Novo", "90.00")));
		when(productRepository.findByAsin("B0X")).thenReturn(Optional.of(existente));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubLimiaresDePreco();

		novoService().executarCicloColeta();

		verify(productRepository).save(argThat(p -> {
			Product produto = (Product) p;
			return produto.getId().equals(42L) && produto.getTitulo().equals("Titulo Novo")
					&& produto.getPrecoAtual().compareTo(new BigDecimal("90.00")) == 0;
		}));
	}

	@Test
	void cadaProdutoProcessadoGravaUmaEntradaDeHistoricoSemDeduplicar() {
		when(categoriaColetaRepository.findByAtivoTrue()).thenReturn(List.of(categoria("MONITOR", "monitor")));
		when(amazonProductScraper.buscarPorKeyword("monitor"))
				.thenReturn(List.of(scraped("B0A", "Produto A", "100.00"), scraped("B0B", "Produto B", "200.00")));
		when(productRepository.findByAsin(any())).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubLimiaresDePreco();

		novoService().executarCicloColeta();

		ArgumentCaptor<PriceHistory> capturado = ArgumentCaptor.forClass(PriceHistory.class);
		verify(priceHistoryRepository, times(2)).save(capturado.capture());

		List<PriceHistory> salvos = capturado.getAllValues();
		assertThat(salvos).extracting(ph -> ph.getProduct().getAsin()).containsExactlyInAnyOrder("B0A", "B0B");
		assertThat(salvos.stream().filter(ph -> ph.getProduct().getAsin().equals("B0A")).findFirst().orElseThrow()
				.getPreco()).isEqualByComparingTo("100.00");
		assertThat(salvos.stream().filter(ph -> ph.getProduct().getAsin().equals("B0B")).findFirst().orElseThrow()
				.getPreco()).isEqualByComparingTo("200.00");
	}

	@Test
	void consultaIntervaloConfiguravelEntreCategorias() {
		when(categoriaColetaRepository.findByAtivoTrue())
				.thenReturn(List.of(categoria("MONITOR", "monitor"), categoria("NOTEBOOK", "notebook")));
		when(amazonProductScraper.buscarPorKeyword(any())).thenReturn(List.of());
		when(configService.getLong(CHAVE_INTERVALO_CATEGORIAS, 5L)).thenReturn(0L);

		novoService().executarCicloColeta();

		verify(configService).getLong(CHAVE_INTERVALO_CATEGORIAS, 5L);
	}

	@Test
	void falhaEmUmaCategoriaNaoInterrompeAsDemais() {
		when(categoriaColetaRepository.findByAtivoTrue())
				.thenReturn(List.of(categoria("MONITOR", "monitor"), categoria("NOTEBOOK", "notebook")));
		when(amazonProductScraper.buscarPorKeyword("monitor")).thenThrow(new RuntimeException("falha simulada"));
		when(amazonProductScraper.buscarPorKeyword("notebook")).thenReturn(List.of(scraped("B0Y", "Notebook Y", "300.00")));
		when(configService.getLong(eq(CHAVE_INTERVALO_CATEGORIAS), anyLong())).thenReturn(0L);
		when(productRepository.findByAsin("B0Y")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		stubLimiaresDePreco();

		Logger logger = (Logger) LoggerFactory.getLogger(ColetaService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			assertDoesNotThrow(() -> novoService().executarCicloColeta());

			verify(productRepository).save(argThat(p -> ((Product) p).getAsin().equals("B0Y")));
			assertThat(appender.list).anyMatch(
					evento -> evento.getLevel() == Level.ERROR && evento.getFormattedMessage().contains("MONITOR"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void cicloComZeroProdutosExtraidosLogaWarnENaoLancaExcecao() {
		when(categoriaColetaRepository.findByAtivoTrue()).thenReturn(List.of(categoria("MONITOR", "monitor")));
		when(amazonProductScraper.buscarPorKeyword("monitor")).thenReturn(List.of());

		Logger logger = (Logger) LoggerFactory.getLogger(ColetaService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			assertDoesNotThrow(() -> novoService().executarCicloColeta());

			assertThat(appender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
					&& evento.getFormattedMessage().contains("ciclo inteiro extraiu zero produtos"));
		} finally {
			logger.detachAppender(appender);
		}
	}

}
