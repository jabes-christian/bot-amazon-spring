package com.jchristian.bot_amazon_spring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.CopyResultadoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyGenerationServiceTest {

	private static final String CHAVE_TIMEOUT = "enriquecimento.llm-timeout-segundos";
	private static final String CHAVE_LIMITE = "enriquecimento.limite-caracteres-copy";
	private static final String TAG_AFILIADO = "meutag-20";

	@Mock
	private ChatModel chatModel;

	@Mock
	private ConfigService configService;

	private ListAppender<ILoggingEvent> logAppender;

	private CopyGenerationService novoService() {
		return new CopyGenerationService(chatModel, configService, TAG_AFILIADO);
	}

	private static Product produto() {
		return Product.builder()
				.id(1L)
				.asin("B0COPY1")
				.titulo("Monitor Gamer 27\" 144Hz")
				.precoAtual(new BigDecimal("899.00"))
				.urlProduto("https://www.amazon.com.br/dp/B0COPY1")
				.build();
	}

	private static CandidatoPromocaoDTO candidato() {
		return new CandidatoPromocaoDTO(produto(), new BigDecimal("25.00"), new BigDecimal("1199.00"));
	}

	private static Product produtoComQuerystring() {
		return Product.builder()
				.id(2L)
				.asin("B0COPY2")
				.titulo("Fone Bluetooth Premium")
				.precoAtual(new BigDecimal("199.00"))
				.urlProduto("https://www.amazon.com.br/dp/B0COPY2?_encoding=UTF8&pd_rd_w=abc123&ref_=pd_hp_d_r_btf_qpp")
				.build();
	}

	private static CandidatoPromocaoDTO candidatoComQuerystring() {
		return new CandidatoPromocaoDTO(produtoComQuerystring(), new BigDecimal("30.00"), new BigDecimal("279.00"));
	}

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(CopyGenerationService.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(CopyGenerationService.class);
		logger.detachAppender(logAppender);
	}

	@Test
	void llmDentroDoTimeoutRetornaViaLlmTrueComTextoDoModelo() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString()))
				.thenReturn("Monitor Gamer 27\" 144Hz\nDe: R$ 1199.00 / Por: R$ 899.00 (-25.00%)\nCorra e aproveite!");

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.viaLlm()).isTrue();
		assertThat(resultado.texto()).contains("Monitor Gamer 27\" 144Hz");
		assertThat(resultado.texto()).contains("1199.00");
		assertThat(resultado.texto()).contains("899.00");
		assertThat(resultado.texto()).contains("25.00%");
		assertThat(resultado.texto()).contains("Corra e aproveite!");
	}

	@Test
	void timeoutExcedidoCaiParaTemplateComViaLlmFalseELogaWarn() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(1L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString())).thenAnswer(invocation -> {
			Thread.sleep(3000);
			return "resposta tardia";
		});

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.viaLlm()).isFalse();
		assertThat(resultado.texto()).contains("Monitor Gamer 27\" 144Hz");
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("falha ao gerar copy via LLM")
				&& evento.getFormattedMessage().contains("B0COPY1"));
	}

	@Test
	void excecaoLancadaPeloLlmCaiParaTemplateComViaLlmFalse() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString())).thenThrow(new RuntimeException("rate limit"));

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.viaLlm()).isFalse();
		assertThat(resultado.texto()).contains("Monitor Gamer 27\" 144Hz");
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("falha ao gerar copy via LLM")
				&& evento.getFormattedMessage().contains("B0COPY1")
				&& evento.getFormattedMessage().contains("rate limit"));
	}

	@Test
	void respostaVaziaDoLlmCaiParaTemplateComViaLlmFalse() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString())).thenReturn("   ");

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.viaLlm()).isFalse();
		assertThat(resultado.texto()).contains("Monitor Gamer 27\" 144Hz");
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("falha ao gerar copy via LLM")
				&& evento.getFormattedMessage().contains("B0COPY1")
				&& evento.getFormattedMessage().contains("resposta vazia do LLM"));
	}

	@Test
	void montarCopyTemplateContemTituloDePorPercentualELinkSemChamarLlmNovamente() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString())).thenThrow(new RuntimeException("falha simulada"));

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.texto()).contains("Monitor Gamer 27\" 144Hz");
		assertThat(resultado.texto()).contains("1199.00");
		assertThat(resultado.texto()).contains("899.00");
		assertThat(resultado.texto()).contains("25.00%");
		assertThat(resultado.texto()).contains("https://www.amazon.com.br/dp/B0COPY1?tag=" + TAG_AFILIADO);
		verify(chatModel, times(1)).chat(anyString());
	}

	@Test
	void linkDeAfiliadoPresenteTantoNoCaminhoLlmQuantoNoTemplate() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		String linkEsperado = "https://www.amazon.com.br/dp/B0COPY1?tag=" + TAG_AFILIADO;

		when(chatModel.chat(anyString())).thenReturn("Copy gerada pelo modelo, sem link.");
		CopyResultadoDTO viaLlm = novoService().gerarCopy(candidato());
		assertThat(viaLlm.texto()).contains(linkEsperado);

		when(chatModel.chat(anyString())).thenThrow(new RuntimeException("indisponivel"));
		CopyResultadoDTO viaTemplate = novoService().gerarCopy(candidato());
		assertThat(viaTemplate.texto()).contains(linkEsperado);
	}

	@Test
	void urlDiferenteDoLinkDeAfiliadoNoTextoDoLlmEhRemovidaESubstituida() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString()))
				.thenReturn("Oferta imperdivel! Veja mais em http://outro-link.exemplo/produto-x");

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.texto()).doesNotContain("outro-link.exemplo");
		assertThat(resultado.texto()).contains("https://www.amazon.com.br/dp/B0COPY1?tag=" + TAG_AFILIADO);
	}

	@Test
	void textoQueExcederiaOLimiteEhTruncadoPreservandoOLinkIntegro() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(80);
		String linkEsperado = "https://www.amazon.com.br/dp/B0COPY1?tag=" + TAG_AFILIADO;
		when(chatModel.chat(anyString())).thenReturn("X".repeat(500));

		CopyResultadoDTO resultado = novoService().gerarCopy(candidato());

		assertThat(resultado.texto().length()).isLessThanOrEqualTo(80);
		assertThat(resultado.texto()).endsWith(linkEsperado);
	}

	@Test
	void urlProdutoComQuerystringExistenteUsaEComercialAntesDaTagEContinuaValida() {
		when(configService.getLong(eq(CHAVE_TIMEOUT), anyLong())).thenReturn(5L);
		when(configService.getInt(eq(CHAVE_LIMITE), anyInt())).thenReturn(1024);
		when(chatModel.chat(anyString())).thenThrow(new RuntimeException("falha simulada"));

		CopyResultadoDTO resultado = novoService().gerarCopy(candidatoComQuerystring());

		String linkEsperado = "https://www.amazon.com.br/dp/B0COPY2?_encoding=UTF8&pd_rd_w=abc123&ref_=pd_hp_d_r_btf_qpp&tag="
				+ TAG_AFILIADO;
		assertThat(resultado.texto()).contains(linkEsperado);
		assertThat(resultado.texto().chars().filter(c -> c == '?').count()).isEqualTo(1);
	}

}
