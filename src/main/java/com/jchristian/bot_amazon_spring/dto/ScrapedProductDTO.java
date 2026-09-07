package com.jchristian.bot_amazon_spring.dto;

import java.math.BigDecimal;

public record ScrapedProductDTO(
		String asin,
		String titulo,
		BigDecimal precoAtual,
		BigDecimal precoRiscado,
		String urlImagem,
		String urlProduto) {

	public ScrapedProductDTO {
		if (asin == null || asin.isBlank()) {
			throw new IllegalArgumentException("asin e obrigatorio");
		}
		if (titulo == null || titulo.isBlank()) {
			throw new IllegalArgumentException("titulo e obrigatorio");
		}
		if (precoAtual == null) {
			throw new IllegalArgumentException("precoAtual e obrigatorio");
		}
	}

	public static ScrapedProductDTO of(String asin, String titulo, BigDecimal precoAtual, BigDecimal precoRiscado,
			String urlImagem, String urlProduto) {
		return new ScrapedProductDTO(asin, titulo, precoAtual, precoRiscado, urlImagem, urlProduto);
	}

}
