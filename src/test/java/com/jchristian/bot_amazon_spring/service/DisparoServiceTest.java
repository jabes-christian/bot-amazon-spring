package com.jchristian.bot_amazon_spring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.DispatchHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
import com.jchristian.bot_amazon_spring.repository.ChannelRepository;
import com.jchristian.bot_amazon_spring.repository.DispatchHistoryRepository;
import com.jchristian.bot_amazon_spring.service.sender.EnvioException;
import com.jchristian.bot_amazon_spring.service.sender.TelegramChannelSender;
import com.jchristian.bot_amazon_spring.service.sender.WhatsAppChannelSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisparoServiceTest {

	private static final String CHAVE_TETO_PRODUTOS = "disparo.teto-produtos-por-canal";
	private static final String CHAVE_JANELA_DEDUP_DIAS = "disparo.janela-dedup-dias";
	private static final String CHAVE_INTERVALO_SEGUNDOS = "disparo.intervalo-entre-envios-segundos";

	@Mock
	private PromotionDetectionService promotionDetectionService;

	@Mock
	private ChannelRepository channelRepository;

	@Mock
	private DispatchHistoryRepository dispatchHistoryRepository;

	@Mock
	private EnriquecimentoService enriquecimentoService;

	@Mock
	private BannerImageService bannerImageService;

	@Mock
	private TelegramChannelSender telegramChannelSender;

	@Mock
	private WhatsAppChannelSender whatsAppChannelSender;

	@Mock
	private ConfigService configService;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(DisparoService.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(DisparoService.class);
		logger.detachAppender(logAppender);
	}

	private DisparoService novoService() {
		return new DisparoService(promotionDetectionService, channelRepository, dispatchHistoryRepository,
				enriquecimentoService, bannerImageService, telegramChannelSender, whatsAppChannelSender, configService);
	}

	private void stubTetoEJanela() {
		when(configService.getInt(eq(CHAVE_TETO_PRODUTOS), anyInt())).thenReturn(5);
		when(configService.getLong(eq(CHAVE_JANELA_DEDUP_DIAS), anyLong())).thenReturn(7L);
	}

	private void stubConfigsPadrao() {
		stubTetoEJanela();
		when(configService.getLong(eq(CHAVE_INTERVALO_SEGUNDOS), anyLong())).thenReturn(0L);
	}

	private void stubSemDedup() {
		when(dispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(any(), any(), any())).thenReturn(false);
	}

	private static CategoriaColeta categoria(String codigo) {
		return CategoriaColeta.builder().id(1L).codigo(codigo).keywordBusca(codigo).ativo(true).build();
	}

	private static Product produto(String asin, CategoriaColeta categoria) {
		return Product.builder().id((long) asin.hashCode()).asin(asin).categoria(categoria).titulo("Produto " + asin)
				.precoAtual(new BigDecimal("100.00")).build();
	}

	private static CandidatoPromocaoDTO candidato(Product produto, String percentual) {
		return new CandidatoPromocaoDTO(produto, new BigDecimal(percentual), new BigDecimal("200.00"));
	}

	private static Channel canal(long id, TipoCanal tipo, String categoriasAceitas) {
		return Channel.builder().id(id).tipo(tipo).identificador("id-" + id).categoriasAceitas(categoriasAceitas)
				.ativo(true).build();
	}

	private static ConteudoEnriquecidoDTO conteudo(boolean viaLlm) {
		return new ConteudoEnriquecidoDTO("copy", null, viaLlm);
	}

	private static ConteudoEnriquecidoDTO conteudoComBanner(Path banner, boolean viaLlm) {
		return new ConteudoEnriquecidoDTO("copy", banner, viaLlm);
	}

	@Test
	void canalComCategoriaRestritaSoRecebeProdutosDessaCategoria() {
		CategoriaColeta monitor = categoria("MONITOR");
		CategoriaColeta notebook = categoria("NOTEBOOK");
		Product produtoMonitor = produto("B0MON", monitor);
		CandidatoPromocaoDTO candidatoMonitor = candidato(produtoMonitor, "20.00");
		Channel canalMonitor = canal(1L, TipoCanal.TELEGRAM, "MONITOR");
		Channel canalNotebook = canal(2L, TipoCanal.TELEGRAM, "NOTEBOOK");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidatoMonitor));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canalMonitor, canalNotebook));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidatoMonitor)).thenReturn(conteudo(true));

		novoService().executarCicloDisparo();

		verify(telegramChannelSender).enviar(eq(canalMonitor), any());
		verify(telegramChannelSender, never()).enviar(eq(canalNotebook), any());
	}

	@Test
	void canalComTipoNaoSuportadoEhIgnoradoComWarn() {
		CategoriaColeta categoria = categoria("MONITOR");
		Channel canalInvalido = canal(1L, null, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of());
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canalInvalido));
		stubTetoEJanela();

		novoService().executarCicloDisparo();

		verify(telegramChannelSender, never()).enviar(any(), any());
		verify(whatsAppChannelSender, never()).enviar(any(), any());
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("tipo nao suportado") && evento.getFormattedMessage().contains("1"));
	}

	@Test
	void candidatosSaoOrdenadosPorPercentualDescendenteELimitadosAoTeto() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produtoA = produto("B0A", categoria);
		Product produtoB = produto("B0B", categoria);
		Product produtoC = produto("B0C", categoria);
		CandidatoPromocaoDTO candidatoA = candidato(produtoA, "10.00");
		CandidatoPromocaoDTO candidatoB = candidato(produtoB, "30.00");
		CandidatoPromocaoDTO candidatoC = candidato(produtoC, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis())
				.thenReturn(List.of(candidatoA, candidatoB, candidatoC));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		when(configService.getInt(eq(CHAVE_TETO_PRODUTOS), anyInt())).thenReturn(2);
		when(configService.getLong(eq(CHAVE_JANELA_DEDUP_DIAS), anyLong())).thenReturn(7L);
		when(configService.getLong(eq(CHAVE_INTERVALO_SEGUNDOS), anyLong())).thenReturn(0L);
		stubSemDedup();
		when(enriquecimentoService.enriquecer(any())).thenReturn(conteudo(true));

		novoService().executarCicloDisparo();

		verify(enriquecimentoService).enriquecer(candidatoB);
		verify(enriquecimentoService).enriquecer(candidatoC);
		verify(enriquecimentoService, never()).enriquecer(candidatoA);
	}

	@Test
	void canalSemCandidatoElegivelEhPuladoSemErro() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "NOTEBOOK");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubTetoEJanela();

		assertDoesNotThrow(() -> novoService().executarCicloDisparo());

		verify(telegramChannelSender, never()).enviar(any(), any());
		verify(enriquecimentoService, never()).enriquecer(any());
	}

	@Test
	void intervaloConfiguradoEhLidoDeConfigServiceParaCadaTentativaDeEnvio() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));

		novoService().executarCicloDisparo();

		verify(configService).getLong(CHAVE_INTERVALO_SEGUNDOS, 2L);
	}

	@Test
	void envioBemSucedidoGravaDispatchHistory() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));

		novoService().executarCicloDisparo();

		ArgumentCaptor<DispatchHistory> captor = ArgumentCaptor.forClass(DispatchHistory.class);
		verify(dispatchHistoryRepository).save(captor.capture());
		assertThat(captor.getValue().getProduct()).isEqualTo(produto);
		assertThat(captor.getValue().getChannel()).isEqualTo(canal);
	}

	@Test
	void produtoJaDedupadoDentroDaJanelaEhExcluidoDaSelecao() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubTetoEJanela();
		when(dispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(eq(produto), eq(canal), any()))
				.thenReturn(true);

		novoService().executarCicloDisparo();

		verify(telegramChannelSender, never()).enviar(any(), any());
		verify(enriquecimentoService, never()).enriquecer(any());
	}

	@Test
	void falhaDeEnvioAcionaExatamenteUmRetryImediatoAntesDeDesistir() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));
		doThrow(new EnvioException("falha temporaria")).doNothing().when(telegramChannelSender).enviar(eq(canal), any());

		novoService().executarCicloDisparo();

		verify(telegramChannelSender, times(2)).enviar(eq(canal), any());
		verify(dispatchHistoryRepository, times(1)).save(any());
	}

	@Test
	void falhaDoRetryLogaErrorENaoGravaDispatchHistory() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));
		doThrow(new EnvioException("indisponivel")).when(telegramChannelSender).enviar(eq(canal), any());

		novoService().executarCicloDisparo();

		verify(telegramChannelSender, times(2)).enviar(eq(canal), any());
		verify(dispatchHistoryRepository, never()).save(any());
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.ERROR
				&& evento.getFormattedMessage().contains("1") && evento.getFormattedMessage().contains("B0X")
				&& evento.getFormattedMessage().contains("indisponivel"));
	}

	@Test
	void falhaEmUmCanalNaoInterrompeOProcessamentoDosDemais() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canalComFalha = canal(1L, TipoCanal.TELEGRAM, "MONITOR");
		Channel canalOk = canal(2L, TipoCanal.WHATSAPP, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canalComFalha, canalOk));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));
		doThrow(new EnvioException("falha")).when(telegramChannelSender).enviar(eq(canalComFalha), any());

		novoService().executarCicloDisparo();

		verify(whatsAppChannelSender).enviar(eq(canalOk), any());
		ArgumentCaptor<DispatchHistory> captor = ArgumentCaptor.forClass(DispatchHistory.class);
		verify(dispatchHistoryRepository).save(captor.capture());
		assertThat(captor.getValue().getChannel()).isEqualTo(canalOk);
	}

	@Test
	void nenhumCanalAtivoLogaInfoEEncerraSemErro() {
		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of());
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of());

		assertDoesNotThrow(() -> novoService().executarCicloDisparo());

		verify(enriquecimentoService, never()).enriquecer(any());
		assertThat(logAppender.list).anyMatch(
				evento -> evento.getLevel() == Level.INFO && evento.getFormattedMessage().contains("nenhum canal ativo"));
	}

	@Test
	void canalComCategoriasAceitasVaziasOuNulasEhIgnoradoComWarn() {
		Channel canalNulo = canal(1L, TipoCanal.TELEGRAM, null);
		Channel canalVazio = canal(2L, TipoCanal.TELEGRAM, "");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of());
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canalNulo, canalVazio));
		stubTetoEJanela();

		novoService().executarCicloDisparo();

		verify(telegramChannelSender, never()).enviar(any(), any());
		assertThat(logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).count()).isEqualTo(2);
	}

	@Test
	void enriquecerEhChamadoExatamenteUmaVezPorProdutoMesmoComMultiplosCanais() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal1 = canal(1L, TipoCanal.TELEGRAM, "MONITOR");
		Channel canal2 = canal(2L, TipoCanal.WHATSAPP, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal1, canal2));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));

		novoService().executarCicloDisparo();

		verify(enriquecimentoService, times(1)).enriquecer(candidato);
		verify(telegramChannelSender).enviar(eq(canal1), any());
		verify(whatsAppChannelSender).enviar(eq(canal2), any());
	}

	@Test
	void removerBannerSoEhChamadoDepoisQueTodosOsCanaisDoProdutoJaTentaramEnviar() throws Exception {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canal1 = canal(1L, TipoCanal.TELEGRAM, "MONITOR");
		Channel canal2 = canal(2L, TipoCanal.WHATSAPP, "MONITOR");
		Path banner = Path.of("banner-teste.jpg");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal1, canal2));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudoComBanner(banner, true));

		novoService().executarCicloDisparo();

		var ordem = inOrder(telegramChannelSender, whatsAppChannelSender, bannerImageService);
		ordem.verify(telegramChannelSender).enviar(eq(canal1), any());
		ordem.verify(whatsAppChannelSender).enviar(eq(canal2), any());
		ordem.verify(bannerImageService).removerBanner(banner);
	}

	@Test
	void logaContagemAgregadaDeCopiasViaLlmECopiasViaTemplateAoFinalDoCiclo() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produtoLlm = produto("B0LLM", categoria);
		Product produtoTemplate = produto("B0TPL", categoria);
		CandidatoPromocaoDTO candidatoLlm = candidato(produtoLlm, "20.00");
		CandidatoPromocaoDTO candidatoTemplate = candidato(produtoTemplate, "15.00");
		Channel canal = canal(1L, TipoCanal.TELEGRAM, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis())
				.thenReturn(List.of(candidatoLlm, candidatoTemplate));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canal));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidatoLlm)).thenReturn(conteudo(true));
		when(enriquecimentoService.enriquecer(candidatoTemplate)).thenReturn(conteudo(false));

		novoService().executarCicloDisparo();

		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.INFO
				&& evento.getFormattedMessage().contains("copiasViaLlm=1")
				&& evento.getFormattedMessage().contains("copiasViaTemplate=1"));
	}

	@Test
	void logaContagemDeEnviosSucessoEFalhaAoFinalDoCiclo() {
		CategoriaColeta categoria = categoria("MONITOR");
		Product produto = produto("B0X", categoria);
		CandidatoPromocaoDTO candidato = candidato(produto, "20.00");
		Channel canalOk = canal(1L, TipoCanal.TELEGRAM, "MONITOR");
		Channel canalComFalha = canal(2L, TipoCanal.WHATSAPP, "MONITOR");

		when(promotionDetectionService.buscarCandidatosElegiveis()).thenReturn(List.of(candidato));
		when(channelRepository.findByAtivoTrue()).thenReturn(List.of(canalOk, canalComFalha));
		stubConfigsPadrao();
		stubSemDedup();
		when(enriquecimentoService.enriquecer(candidato)).thenReturn(conteudo(true));
		doThrow(new EnvioException("falha")).when(whatsAppChannelSender).enviar(eq(canalComFalha), any());

		novoService().executarCicloDisparo();

		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.INFO
				&& evento.getFormattedMessage().startsWith("DISPARO: ciclo concluido")
				&& evento.getFormattedMessage().contains("enviosSucesso=1")
				&& evento.getFormattedMessage().contains("enviosFalha=1"));
	}

}
