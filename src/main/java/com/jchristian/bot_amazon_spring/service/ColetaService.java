package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.ScrapedProductDTO;
import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.PriceHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.CategoriaColetaRepository;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import com.jchristian.bot_amazon_spring.repository.ProductRepository;
import com.jchristian.bot_amazon_spring.scraper.AmazonProductScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColetaService {

	private static final String CHAVE_PRECO_MINIMO = "coleta.preco-minimo-valido";
	private static final String CHAVE_PRECO_MAXIMO = "coleta.preco-maximo-valido";
	private static final String CHAVE_INTERVALO_CATEGORIAS = "coleta.intervalo-entre-categorias-segundos";

	private static final BigDecimal PRECO_MINIMO_PADRAO = new BigDecimal("0.01");
	private static final BigDecimal PRECO_MAXIMO_PADRAO = new BigDecimal("50000.00");
	private static final long INTERVALO_PADRAO_SEGUNDOS = 5L;

	private final AmazonProductScraper amazonProductScraper;
	private final CategoriaColetaRepository categoriaColetaRepository;
	private final ProductRepository productRepository;
	private final PriceHistoryRepository priceHistoryRepository;
	private final ConfigService configService;

	public void executarCicloColeta() {
		List<CategoriaColeta> categorias = categoriaColetaRepository.findByAtivoTrue();
		int totalProdutosExtraidos = 0;
		int falhasCategoria = 0;

		for (int i = 0; i < categorias.size(); i++) {
			CategoriaColeta categoria = categorias.get(i);
			try {
				totalProdutosExtraidos += processarCategoria(categoria);
			} catch (Exception e) {
				falhasCategoria++;
				log.error("COLETA: falha ao processar categoria {}", categoria.getCodigo(), e);
			}
			if (i < categorias.size() - 1) {
				aguardarIntervaloEntreCategorias();
			}
		}

		if (totalProdutosExtraidos == 0) {
			log.warn("COLETA: ciclo inteiro extraiu zero produtos");
		}

		log.info("COLETA: ciclo concluido - categoriasProcessadas={}, produtosExtraidos={}, falhasCategoria={}",
				categorias.size(), totalProdutosExtraidos, falhasCategoria);
	}

	private int processarCategoria(CategoriaColeta categoria) {
		List<ScrapedProductDTO> produtosEncontrados = amazonProductScraper.buscarPorKeyword(categoria.getKeywordBusca());

		if (produtosEncontrados.isEmpty()) {
			log.warn("COLETA: categoria sem resultado - categoria={}, keyword={}", categoria.getCodigo(),
					categoria.getKeywordBusca());
		}

		for (ScrapedProductDTO scraped : produtosEncontrados) {
			processarProduto(categoria, scraped);
		}

		return produtosEncontrados.size();
	}

	private void processarProduto(CategoriaColeta categoria, ScrapedProductDTO scraped) {
		BigDecimal precoMinimo = configService.getBigDecimal(CHAVE_PRECO_MINIMO, PRECO_MINIMO_PADRAO);
		BigDecimal precoMaximo = configService.getBigDecimal(CHAVE_PRECO_MAXIMO, PRECO_MAXIMO_PADRAO);

		if (scraped.precoAtual().compareTo(precoMinimo) < 0 || scraped.precoAtual().compareTo(precoMaximo) > 0) {
			log.warn("COLETA: preco fora da faixa de sanidade - asin={}, preco={}", scraped.asin(), scraped.precoAtual());
			return;
		}

		Product product = productRepository.findByAsin(scraped.asin())
				.map(existente -> atualizarProduct(existente, scraped))
				.orElseGet(() -> criarProduct(categoria, scraped));

		product = productRepository.save(product);

		priceHistoryRepository.save(PriceHistory.builder().product(product).preco(scraped.precoAtual()).build());
	}

	private Product criarProduct(CategoriaColeta categoria, ScrapedProductDTO scraped) {
		return Product.builder()
				.asin(scraped.asin())
				.categoria(categoria)
				.titulo(scraped.titulo())
				.precoAtual(scraped.precoAtual())
				.precoRiscado(scraped.precoRiscado())
				.urlImagem(scraped.urlImagem())
				.urlProduto(scraped.urlProduto())
				.build();
	}

	private Product atualizarProduct(Product existente, ScrapedProductDTO scraped) {
		existente.setTitulo(scraped.titulo());
		existente.setPrecoAtual(scraped.precoAtual());
		existente.setPrecoRiscado(scraped.precoRiscado());
		existente.setUrlImagem(scraped.urlImagem());
		return existente;
	}

	private void aguardarIntervaloEntreCategorias() {
		long segundos = configService.getLong(CHAVE_INTERVALO_CATEGORIAS, INTERVALO_PADRAO_SEGUNDOS);
		try {
			Thread.sleep(Duration.ofSeconds(segundos));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
