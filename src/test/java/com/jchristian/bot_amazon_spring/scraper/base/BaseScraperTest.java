package com.jchristian.bot_amazon_spring.scraper.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseScraperTest {

	@Mock
	private WebDriver driver;

	@Mock
	private WebElement element;

	private BaseScraper scraper;

	@BeforeEach
	void setUp() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(300));
		scraper = new BaseScraper(driver, wait) {
		};
	}

	@Test
	void navegarParaChamaDriverGetComAUrlRecebida() {
		scraper.navegarPara("https://www.amazon.com.br/s?k=monitor");

		// navegarPara é um repasse direto para driver.get(url), void, sem estado observável
		// além da própria chamada (o WebDriver real é mockado) — verify() com o argumento
		// exato é a asserção mais forte possível aqui, não um substituto para uma checagem
		// de estado que exista e esteja sendo pulada.
		org.mockito.Mockito.verify(driver).get("https://www.amazon.com.br/s?k=monitor");
	}

	@Test
	void aguardarElementosRetornaOsElementosEncontrados() {
		By locator = By.cssSelector("div.card");
		when(driver.findElements(locator)).thenReturn(List.of(element, element));

		List<WebElement> encontrados = scraper.aguardarElementos(locator);

		assertThat(encontrados).hasSize(2);
	}

	@Test
	void extrairTextoRetornaOTextoDoElementoEncontrado() {
		By locator = By.cssSelector("h2 span");
		when(driver.findElement(locator)).thenReturn(element);
		when(element.getText()).thenReturn("Monitor Gamer 144hz");

		String texto = scraper.extrairTexto(locator);

		assertThat(texto).isEqualTo("Monitor Gamer 144hz");
	}

	@Test
	void extrairAtributoRetornaOValorDoAtributoDoElementoEncontrado() {
		By locator = By.cssSelector("h2 a");
		when(driver.findElement(locator)).thenReturn(element);
		when(element.getAttribute("href")).thenReturn("https://www.amazon.com.br/dp/B0EXEMPLO");

		String href = scraper.extrairAtributo(locator, "href");

		assertThat(href).isEqualTo("https://www.amazon.com.br/dp/B0EXEMPLO");
	}

	@Test
	void elementoExisteRetornaTrueQuandoElementoEEncontrado() {
		By locator = By.cssSelector("span.a-price");
		when(driver.findElement(locator)).thenReturn(element);

		assertThat(scraper.elementoExiste(locator)).isTrue();
	}

	@Test
	void elementoExisteRetornaFalseQuandoElementoNaoEEncontrado() {
		By locator = By.cssSelector("span.nao-existe");
		when(driver.findElement(any(By.class))).thenThrow(new NoSuchElementException("nao encontrado"));

		assertThat(scraper.elementoExiste(locator)).isFalse();
	}

}
