package com.jchristian.bot_amazon_spring.scraper;

import com.jchristian.bot_amazon_spring.config.AmazonSelectorsProperties;
import com.jchristian.bot_amazon_spring.dto.ScrapedProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmazonProductScraperTest {

	@Mock
	private WebDriver driver;

	@Mock
	private WebElement card;

	@Mock
	private WebElement tituloElemento;

	@Mock
	private WebElement precoAtualElemento;

	@Mock
	private WebElement precoRiscadoElemento;

	@Mock
	private WebElement imagemElemento;

	@Mock
	private WebElement linkElemento;

	private final AmazonSelectorsProperties selectors = new AmazonSelectorsProperties();

	private AmazonProductScraper scraper;

	@BeforeEach
	void setUp() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(300));
		scraper = new AmazonProductScraper(driver, wait, selectors);
	}

	@Test
	void buscarPorKeywordNavegaParaAUrlDeBuscaCorreta() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer()))).thenReturn(List.of(card));
		when(card.getAttribute(selectors.getAsinAttributo())).thenReturn(null);

		scraper.buscarPorKeyword("monitor gamer");

		verify(driver).get("https://www.amazon.com.br/s?k=monitor+gamer");
	}

	@Test
	void buscarPorKeywordExtraiTodosOsCamposDeUmCardValido() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer()))).thenReturn(List.of(card));
		when(card.getAttribute(selectors.getAsinAttributo())).thenReturn("B0EXEMPLO");
		when(card.findElement(By.cssSelector(selectors.getPrecoAtual()))).thenReturn(precoAtualElemento);
		when(precoAtualElemento.getText()).thenReturn("R$ 1.299,00");
		when(card.findElement(By.cssSelector(selectors.getTitulo()))).thenReturn(tituloElemento);
		when(tituloElemento.getText()).thenReturn("Monitor Gamer 144hz");
		when(card.findElement(By.cssSelector(selectors.getPrecoRiscado()))).thenReturn(precoRiscadoElemento);
		when(precoRiscadoElemento.getText()).thenReturn("R$ 1.599,00");
		when(card.findElement(By.cssSelector(selectors.getImagem()))).thenReturn(imagemElemento);
		when(imagemElemento.getAttribute("src")).thenReturn("https://img.example/foto.jpg");
		when(card.findElement(By.cssSelector(selectors.getLink()))).thenReturn(linkElemento);
		when(linkElemento.getAttribute("href")).thenReturn("https://www.amazon.com.br/dp/B0EXEMPLO");

		List<ScrapedProductDTO> produtos = scraper.buscarPorKeyword("monitor gamer");

		assertThat(produtos).hasSize(1);
		ScrapedProductDTO produto = produtos.get(0);
		assertThat(produto.asin()).isEqualTo("B0EXEMPLO");
		assertThat(produto.titulo()).isEqualTo("Monitor Gamer 144hz");
		assertThat(produto.precoAtual()).isEqualByComparingTo("1299.00");
		assertThat(produto.precoRiscado()).isEqualByComparingTo("1599.00");
		assertThat(produto.urlImagem()).isEqualTo("https://img.example/foto.jpg");
		assertThat(produto.urlProduto()).isEqualTo("https://www.amazon.com.br/dp/B0EXEMPLO");
	}

	@Test
	void cardSemAsinEDescartado() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer()))).thenReturn(List.of(card));
		when(card.getAttribute(selectors.getAsinAttributo())).thenReturn(null);

		List<ScrapedProductDTO> produtos = scraper.buscarPorKeyword("monitor gamer");

		assertThat(produtos).isEmpty();
	}

	@Test
	void cardSemPrecoAtualEDescartado() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer()))).thenReturn(List.of(card));
		when(card.getAttribute(selectors.getAsinAttributo())).thenReturn("B0EXEMPLO");
		when(card.findElement(By.cssSelector(selectors.getPrecoAtual())))
				.thenThrow(new NoSuchElementException("nao encontrado"));

		List<ScrapedProductDTO> produtos = scraper.buscarPorKeyword("monitor gamer");

		assertThat(produtos).isEmpty();
	}

	@Test
	void buscarPorKeywordRetornaListaVaziaQuandoZeroCardsEncontrados() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer()))).thenReturn(List.of());

		List<ScrapedProductDTO> produtos = scraper.buscarPorKeyword("produto sem resultado nenhum");

		assertThat(produtos).isEmpty();
	}

	@Test
	void buscarPorKeywordPropagaExcecoesQueNaoSaoTimeout() {
		when(driver.findElements(By.cssSelector(selectors.getCardContainer())))
				.thenThrow(new WebDriverException("driver morto"));

		assertThrows(WebDriverException.class, () -> scraper.buscarPorKeyword("monitor gamer"));
	}

}
