package com.jchristian.bot_amazon_spring.scraper;

import com.jchristian.bot_amazon_spring.config.AmazonSelectorsProperties;
import com.jchristian.bot_amazon_spring.dto.ScrapedProductDTO;
import com.jchristian.bot_amazon_spring.scraper.base.BaseScraper;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmazonProductScraper extends BaseScraper {

	private static final String BASE_SEARCH_URL = "https://www.amazon.com.br/s?k=";

	private final AmazonSelectorsProperties selectors;

	public AmazonProductScraper(WebDriver driver, WebDriverWait wait, AmazonSelectorsProperties selectors) {
		super(driver, wait);
		this.selectors = selectors;
	}

	public List<ScrapedProductDTO> buscarPorKeyword(String keyword) {
		navegarPara(BASE_SEARCH_URL + URLEncoder.encode(keyword, StandardCharsets.UTF_8));

		List<WebElement> cards;
		try {
			cards = aguardarElementos(By.cssSelector(selectors.getCardContainer()));
		} catch (TimeoutException e) {
			return List.of();
		}

		List<ScrapedProductDTO> produtos = new ArrayList<>();
		for (WebElement card : cards) {
			ScrapedProductDTO produto = extrairProduto(card);
			if (produto != null) {
				produtos.add(produto);
			}
		}
		return produtos;
	}

	private ScrapedProductDTO extrairProduto(WebElement card) {
		String asin = card.getAttribute(selectors.getAsinAttributo());
		if (asin == null || asin.isBlank()) {
			return null;
		}

		BigDecimal precoAtual = parsePreco(extrairTextoDoCard(card, selectors.getPrecoAtual()));
		if (precoAtual == null) {
			return null;
		}

		String titulo = extrairTextoDoCard(card, selectors.getTitulo());
		BigDecimal precoRiscado = parsePreco(extrairTextoDoCard(card, selectors.getPrecoRiscado()));
		String urlImagem = extrairAtributoDoCard(card, selectors.getImagem(), "src");
		String urlProduto = extrairAtributoDoCard(card, selectors.getLink(), "href");

		try {
			return ScrapedProductDTO.of(asin, titulo, precoAtual, precoRiscado, urlImagem, urlProduto);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String extrairTextoDoCard(WebElement card, String seletor) {
		try {
			return card.findElement(By.cssSelector(seletor)).getText();
		} catch (NoSuchElementException e) {
			return null;
		}
	}

	private String extrairAtributoDoCard(WebElement card, String seletor, String atributo) {
		try {
			return card.findElement(By.cssSelector(seletor)).getAttribute(atributo);
		} catch (NoSuchElementException e) {
			return null;
		}
	}

	private BigDecimal parsePreco(String textoPreco) {
		if (textoPreco == null || textoPreco.isBlank()) {
			return null;
		}
		String normalizado = textoPreco.replaceAll("[^0-9,.]", "").replace(".", "").replace(",", ".");
		try {
			return new BigDecimal(normalizado);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
