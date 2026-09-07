package com.jchristian.bot_amazon_spring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amazon.selectors")
@Getter
@Setter
public class AmazonSelectorsProperties {

	private String cardContainer = "div[data-component-type=\"s-search-result\"]";
	private String asinAttributo = "data-asin";
	private String titulo = "h2 span";
	private String precoAtual = "span.a-price span.a-offscreen";
	private String precoRiscado = "span.a-price.a-text-price span.a-offscreen";
	private String imagem = "img.s-image";
	private String link = "h2 a";

}
