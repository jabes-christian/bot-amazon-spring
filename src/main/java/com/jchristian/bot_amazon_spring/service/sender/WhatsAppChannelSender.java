package com.jchristian.bot_amazon_spring.service.sender;

import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class WhatsAppChannelSender implements ChannelSender {

	// O LLM produz Markdown padrao (CommonMark): **negrito** e ~~tachado~~. O WhatsApp usa um
	// unico caractere para cada enfase: *negrito* e ~tachado~ (ver Formatting messages na doc da
	// Evolution/WhatsApp Business API). Sem essa conversao o usuario final veria os marcadores
	// duplicados de forma literal.
	private static final Pattern NEGRITO_MARKDOWN_PADRAO = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern TACHADO_MARKDOWN_PADRAO = Pattern.compile("~~(.+?)~~");

	private final RestClient restClient;
	private final String apiUrl;
	private final String apiKey;
	private final String instance;

	public WhatsAppChannelSender(RestClient.Builder restClientBuilder, @Value("${evolution.api.url:}") String apiUrl,
			@Value("${evolution.api.key:}") String apiKey, @Value("${evolution.api.instance:}") String instance) {
		this.restClient = restClientBuilder.build();
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
		this.instance = instance;
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
					"falha ao enviar via WhatsApp - canal=" + canal.getIdentificador() + ", motivo=" + e.getMessage(), e);
		}
	}

	private void enviarComBanner(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		String mediaBase64 = lerBannerComoBase64(conteudo);

		Map<String, Object> corpo = Map.of(
				"number", canal.getIdentificador(),
				"mediatype", "image",
				"mimetype", "image/jpeg",
				"fileName", "banner.jpg",
				"media", mediaBase64,
				"caption", converterParaSintaxeWhatsApp(conteudo.copy()));

		restClient.post()
				.uri(apiUrl + "/message/sendMedia/" + instance)
				.header("ApiKey", apiKey)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

	private void enviarTextoPuro(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		Map<String, String> corpo = Map.of("number", canal.getIdentificador(), "text",
				converterParaSintaxeWhatsApp(conteudo.copy()));

		restClient.post()
				.uri(apiUrl + "/message/sendText/" + instance)
				.header("ApiKey", apiKey)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

	private String converterParaSintaxeWhatsApp(String texto) {
		String comNegritoConvertido = NEGRITO_MARKDOWN_PADRAO.matcher(texto).replaceAll("*$1*");
		return TACHADO_MARKDOWN_PADRAO.matcher(comNegritoConvertido).replaceAll("~$1~");
	}

	private String lerBannerComoBase64(ConteudoEnriquecidoDTO conteudo) {
		try {
			return Base64.getEncoder().encodeToString(Files.readAllBytes(conteudo.bannerPath()));
		} catch (IOException e) {
			throw new EnvioException("falha ao ler arquivo de banner para envio via WhatsApp", e);
		}
	}

}
