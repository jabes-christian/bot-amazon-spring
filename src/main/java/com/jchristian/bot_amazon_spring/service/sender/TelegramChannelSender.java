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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TelegramChannelSender implements ChannelSender {

	private static final String BASE_URL = "https://api.telegram.org";
	private static final String PARSE_MODE = "MarkdownV2";

	// Telegram MarkdownV2 usa um unico caractere para enfase (*negrito*, ~tachado~), diferente do
	// Markdown padrao (CommonMark) que os LLMs produzem (**negrito**, ~~tachado~~) - ver
	// core.telegram.org/bots/api#markdownv2-style. Fora dessas marcacoes, os caracteres abaixo
	// precisam ser escapados ou a API do Telegram rejeita a mensagem inteira.
	private static final String CARACTERES_ESPECIAIS_MARKDOWNV2 = "_*[]()~`>#+-=|{}.!\\";
	private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
	private static final Pattern MARCADOR_ENFASE = Pattern.compile("(\\*\\*|~~)(.+?)\\1");

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
		corpo.add("caption", converterParaMarkdownV2(conteudo.copy()));
		corpo.add("parse_mode", PARSE_MODE);
		corpo.add("photo", new FileSystemResource(conteudo.bannerPath()));

		restClient.post()
				.uri(BASE_URL + "/bot{token}/sendPhoto", botToken)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

	private void enviarTextoPuro(Channel canal, ConteudoEnriquecidoDTO conteudo) {
		Map<String, String> corpo = Map.of("chat_id", canal.getIdentificador(), "text",
				converterParaMarkdownV2(conteudo.copy()), "parse_mode", PARSE_MODE);

		restClient.post()
				.uri(BASE_URL + "/bot{token}/sendMessage", botToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
	}

	// Nunca escapa o link de afiliado: e-lo escapado o exibiria com barras invertidas visiveis
	// (ex.: "amazon\.com\.br") e poderia impedir o auto-link do Telegram para a URL.
	private String converterParaMarkdownV2(String texto) {
		StringBuilder resultado = new StringBuilder();
		Matcher urlMatcher = URL_PATTERN.matcher(texto);
		int ultimoFim = 0;
		while (urlMatcher.find()) {
			resultado.append(formatarComEnfase(texto.substring(ultimoFim, urlMatcher.start())));
			resultado.append(urlMatcher.group());
			ultimoFim = urlMatcher.end();
		}
		resultado.append(formatarComEnfase(texto.substring(ultimoFim)));
		return resultado.toString();
	}

	private String formatarComEnfase(String segmento) {
		StringBuilder resultado = new StringBuilder();
		Matcher matcher = MARCADOR_ENFASE.matcher(segmento);
		int ultimoFim = 0;
		while (matcher.find()) {
			resultado.append(escaparCaracteresEspeciais(segmento.substring(ultimoFim, matcher.start())));
			String marcador = matcher.group(1).equals("**") ? "*" : "~";
			resultado.append(marcador).append(escaparCaracteresEspeciais(matcher.group(2))).append(marcador);
			ultimoFim = matcher.end();
		}
		resultado.append(escaparCaracteresEspeciais(segmento.substring(ultimoFim)));
		return resultado.toString();
	}

	private String escaparCaracteresEspeciais(String texto) {
		StringBuilder escapado = new StringBuilder();
		for (int i = 0; i < texto.length(); i++) {
			char c = texto.charAt(i);
			if (CARACTERES_ESPECIAIS_MARKDOWNV2.indexOf(c) >= 0) {
				escapado.append('\\');
			}
			escapado.append(c);
		}
		return escapado.toString();
	}

}
