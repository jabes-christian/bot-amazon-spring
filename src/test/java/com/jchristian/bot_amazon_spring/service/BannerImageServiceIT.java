package com.jchristian.bot_amazon_spring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(BannerImageService.class)
class BannerImageServiceIT {

	private static final String CHAVE_SELO_DIAS = "enriquecimento.selo-menor-preco-dias";
	private static final String URL_IMAGEM = "https://images.amazon.com.br/B0BANNER1.jpg";

	@Autowired
	private BannerImageService bannerImageService;

	@Autowired
	private MockRestServiceServer server;

	@MockitoBean
	private ConfigService configService;

	@MockitoBean
	private PriceHistoryRepository priceHistoryRepository;

	private ListAppender<ILoggingEvent> logAppender;

	private static Product produto() {
		return Product.builder()
				.id(1L)
				.asin("B0BANNER1")
				.titulo("Monitor Gamer 27\"")
				.precoAtual(new BigDecimal("899.00"))
				.urlImagem(URL_IMAGEM)
				.build();
	}

	private static CandidatoPromocaoDTO candidato() {
		return new CandidatoPromocaoDTO(produto(), new BigDecimal("25.00"), new BigDecimal("1199.00"));
	}

	private static byte[] imagemJpegSolida(Color cor) throws IOException {
		BufferedImage imagem = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = imagem.createGraphics();
		g.setColor(cor);
		g.fillRect(0, 0, 300, 300);
		g.dispose();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(imagem, "jpg", baos);
		return baos.toByteArray();
	}

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(BannerImageService.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(BannerImageService.class);
		logger.detachAppender(logAppender);
	}

	@Test
	void downloadComSucessoEImagemValidaGeraBannerJpeg800x800() throws IOException {
		when(configService.getInt(eq(CHAVE_SELO_DIAS), any(Integer.class))).thenReturn(90);
		when(priceHistoryRepository.findMenorPrecoDesde(any(), any())).thenReturn(Optional.empty());
		server.expect(requestTo(URL_IMAGEM)).andRespond(withSuccess(imagemJpegSolida(Color.GREEN), MediaType.IMAGE_JPEG));

		Optional<Path> resultado = bannerImageService.gerarBanner(candidato());

		assertThat(resultado).isPresent();
		try {
			BufferedImage banner = ImageIO.read(resultado.get().toFile());
			assertThat(banner.getWidth()).isEqualTo(800);
			assertThat(banner.getHeight()).isEqualTo(800);
			assertThat(resultado.get().toString()).endsWith(".jpg");
		} finally {
			bannerImageService.removerBanner(resultado.get());
		}
	}

	@Test
	void downloadComErroHttpRetornaOptionalEmptyELogaWarnComAsinEMotivo() {
		server.expect(requestTo(URL_IMAGEM)).andRespond(withServerError());

		Optional<Path> resultado = bannerImageService.gerarBanner(candidato());

		assertThat(resultado).isEmpty();
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("falha ao baixar imagem")
				&& evento.getFormattedMessage().contains("B0BANNER1"));
	}

	@Test
	void conteudoBaixadoNaoEhImagemValidaRetornaOptionalEmptyELogaWarn() {
		server.expect(requestTo(URL_IMAGEM))
				.andRespond(withSuccess("isto nao e uma imagem".getBytes(), MediaType.TEXT_PLAIN));

		Optional<Path> resultado = bannerImageService.gerarBanner(candidato());

		assertThat(resultado).isEmpty();
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("nao e uma imagem valida")
				&& evento.getFormattedMessage().contains("B0BANNER1"));
	}

	@Test
	void removerBannerApagaOArquivo() throws IOException {
		Path arquivo = Files.createTempFile("banner-teste-", ".jpg");
		assertThat(Files.exists(arquivo)).isTrue();

		bannerImageService.removerBanner(arquivo);

		assertThat(Files.exists(arquivo)).isFalse();
	}

	@Test
	void precoAtualIgualAoMenorDosUltimosDiasIncluiSeloNoBanner() throws IOException {
		when(configService.getInt(eq(CHAVE_SELO_DIAS), any(Integer.class))).thenReturn(90);
		when(priceHistoryRepository.findMenorPrecoDesde(any(), any(LocalDateTime.class)))
				.thenReturn(Optional.of(new BigDecimal("899.00")));
		server.expect(requestTo(URL_IMAGEM)).andRespond(withSuccess(imagemJpegSolida(Color.GREEN), MediaType.IMAGE_JPEG));

		Optional<Path> resultado = bannerImageService.gerarBanner(candidato());

		assertThat(resultado).isPresent();
		try {
			BufferedImage banner = ImageIO.read(resultado.get().toFile());
			int rgb = banner.getRGB(700, 25);
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			assertThat(r).isGreaterThan(150);
			assertThat(g).isLessThan(100);
			assertThat(b).isLessThan(100);
		} finally {
			bannerImageService.removerBanner(resultado.get());
		}
	}

}
