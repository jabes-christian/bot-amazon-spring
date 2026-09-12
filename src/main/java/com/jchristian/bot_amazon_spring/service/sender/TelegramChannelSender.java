package com.jchristian.bot_amazon_spring.service.sender;

import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
@Slf4j
public class TelegramChannelSender implements ChannelSender {

	private static final String BASE_URL = "https://api.telegram.org";

	private final RestClient restClient;
	private final String botToken;

	public TelegramChannelSender(RestClient.Builder restClientBuilder, @Value("${telegram.bot.token:}") String botToken) {
		this.restClient = restClientBuilder.build();
		this.botToken = botToken;
	}

	@Override
	public void enviar(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		try {
			if (conteudo.bannerPath() != null) {
				enviarComBanner(canal, conteudo);
			} else {
				enviarTextoPuro(canal, conteudo);
			}
		} catch (RestClientException e) {
			throw new EnvioException(
					"falha ao enviar via Telegram - canal=" + canal.getIdentificador() + ", motivo=" + e.getMessage(), e);
		}
	}

	private void enviarComBanner(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
		corpo.add("chat_id", canal.getIdentificador());
		corpo.add("caption", conteudo.copy());
		corpo.add("photo", new FileSystemResource(conteudo.bannerPath()));

		restClient.post()
				.uri(BASE_URL + "/bot{token}/sendPhoto", botToken)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

	private void enviarTextoPuro(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		Map<String, String> corpo = Map.of("chat_id", canal.getIdentificador(), "text", conteudo.copy());

		restClient.post()
				.uri(BASE_URL + "/bot{token}/sendMessage", botToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

}
