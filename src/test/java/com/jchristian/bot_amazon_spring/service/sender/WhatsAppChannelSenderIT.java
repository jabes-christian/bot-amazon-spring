package com.jchristian.bot_amazon_spring.service.sender;

import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(WhatsAppChannelSender.class)
@TestPropertySource(properties = { "evolution.api.url=https://evolution.teste.com", "evolution.api.key=TESTKEY",
		"evolution.api.instance=bot-amazon" })
class WhatsAppChannelSenderIT {

	@Autowired
	private WhatsAppChannelSender whatsAppChannelSender;

	@Autowired
	private MockRestServiceServer server;

	private static Channel canal() {
		return Channel.builder().id(1L).tipo(TipoCanal.WHATSAPP).identificador("5511999999999").ativo(true).build();
	}

	@Test
	void produtoComBannerChamaSendMediaComBase64ECaption() throws IOException {
		Path banner = Files.createTempFile("banner-teste-", ".jpg");
		Files.write(banner, "conteudo-fake-da-imagem".getBytes());
		try {
			ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy do produto", banner, true);
			String base64Esperado = Base64.getEncoder().encodeToString(Files.readAllBytes(banner));

			server.expect(requestTo("https://evolution.teste.com/message/sendMedia/bot-amazon"))
					.andExpect(method(HttpMethod.POST))
					.andExpect(header("ApiKey", "TESTKEY"))
					.andExpect(content().string(containsString("\"number\":\"5511999999999\"")))
					.andExpect(content().string(containsString("\"media\":\"" + base64Esperado + "\"")))
					.andExpect(content().string(containsString("\"caption\":\"copy do produto\"")))
					.andRespond(withSuccess("{\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

			whatsAppChannelSender.enviar(canal(), conteudo);

			server.verify();
		} finally {
			Files.deleteIfExists(banner);
		}
	}

	@Test
	void produtoEmModoTextoPuroChamaSendTextComTextoENuncaSendMedia() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://evolution.teste.com/message/sendText/bot-amazon"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("ApiKey", "TESTKEY"))
				.andExpect(content().json("{\"number\":\"5511999999999\",\"text\":\"copy sem banner\"}"))
				.andRespond(withSuccess("{\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

		whatsAppChannelSender.enviar(canal(), conteudo);

		server.verify();
	}

	@Test
	void respostaHttp201ComStatusPendingNoCorpoETratadaComoSucesso() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://evolution.teste.com/message/sendText/bot-amazon"))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"status\":\"PENDING\",\"key\":{\"id\":\"ABC123\"}}"));

		whatsAppChannelSender.enviar(canal(), conteudo);

		server.verify();
	}

	@Test
	void respostaHttpNaoSucessoLancaEnvioException() {
		ConteudoEnriquecidoDTO conteudo = new ConteudoEnriquecidoDTO("copy sem banner", null, false);

		server.expect(requestTo("https://evolution.teste.com/message/sendText/bot-amazon")).andRespond(withServerError());

		assertThatThrownBy(() -> whatsAppChannelSender.enviar(canal(), conteudo)).isInstanceOf(EnvioException.class);
	}

}
