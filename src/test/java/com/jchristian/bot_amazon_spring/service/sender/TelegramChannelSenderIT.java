package com.jchristian.bot_amazon_spring.service.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(TelegramChannelSender.class)
@TestPropertySource(properties = "telegram.bot.token=TESTTOKEN")
class TelegramChannelSenderIT {

	@Autowired
	private TelegramChannelSender telegramChannelSender;

	@Autowired
	private MockRestServiceServer server;

	private static Channel canal() {
		return Channel.builder().id(1L).tipo(TipoCanal.TELEGRAM).identificador("-100123456").ativo(true).build();
	}

	@Test
	void produtoComBannerChamaSendPhotoComMultipartECaption() throws IOException {
		Path banner = Files.createTempFile("banner-teste-", ".jpg");
		try {
			ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy do produto", banner, true);

			server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendPhoto"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header("Content-Type", startsWith("multipart/form-data")))
					.andExpect(content().string(containsString("name=\"chat_id\"")))
					.andExpect(content().string(containsString("-100123456")))
					.andExpect(content().string(containsString("name=\"caption\"")))
					.andExpect(content().string(containsString("copy do produto")))
					.andExpect(content().string(containsString("name=\"photo\"")))
					.andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

			telegramChannelSender.enviar(canal(), conteudo);

			server.verify();
		} finally {
			Files.deleteIfExists(banner);
		}
	}

	@Test
	void produtoEmModoTextoPuroChamaSendMessageComTextoENuncaSendPhoto() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendMessage"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"chat_id\":\"-100123456\",\"text\":\"copy sem banner\"}"))
				.andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

		telegramChannelSender.enviar(canal(), conteudo);

		server.verify();
	}

	@Test
	void produtoEmModoTextoPuroEnviaComParseModeMarkdownV2() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendMessage"))
				.andExpect(content().string(containsString("\"parse_mode\":\"MarkdownV2\"")))
				.andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

		telegramChannelSender.enviar(canal(), conteudo);

		server.verify();
	}

	@Test
	void copyComEnfaseECaracteresEspeciaisEhConvertidaParaMarkdownV2ComUrlPreservada() throws Exception {
		String copy = "Fone Bluetooth\nDe: ~~R$ 199.00~~ Por: **R$ 149.00** (-25%!)\n\n"
				+ "https://www.amazon.com.br/dp/B0X?tag=meutag-20";
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO(copy, null, true);

		String textoEsperado = "Fone Bluetooth\nDe: ~R$ 199\\.00~ Por: *R$ 149\\.00* \\(\\-25%\\!\\)\n\n"
				+ "https://www.amazon.com.br/dp/B0X?tag=meutag-20";
		String jsonEsperado = new ObjectMapper().writeValueAsString(
				Map.of("chat_id", "-100123456", "text", textoEsperado, "parse_mode", "MarkdownV2"));

		server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendMessage"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json(jsonEsperado))
				.andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

		telegramChannelSender.enviar(canal(), conteudo);

		server.verify();
	}

	@Test
	void produtoComBannerEnviaCaptionConvertidaParaMarkdownV2ComParseMode() throws IOException {
		Path banner = Files.createTempFile("banner-teste-", ".jpg");
		try {
			ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("**Oferta!**", banner, true);

			server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendPhoto"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(content().string(containsString("name=\"caption\"")))
					.andExpect(content().string(containsString("*Oferta\\!*")))
					.andExpect(content().string(containsString("name=\"parse_mode\"")))
					.andExpect(content().string(containsString("MarkdownV2")))
					.andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

			telegramChannelSender.enviar(canal(), conteudo);

			server.verify();
		} finally {
			Files.deleteIfExists(banner);
		}
	}

	@Test
	void respostaHttpNaoSucessoLancaEnvioException() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://api.telegram.org/botTESTTOKEN/sendMessage")).andRespond(withServerError());

		assertThatThrownBy(() -> telegramChannelSender.enviar(canal(), conteudo)).isInstanceOf(EnvioException.class);
	}

}
