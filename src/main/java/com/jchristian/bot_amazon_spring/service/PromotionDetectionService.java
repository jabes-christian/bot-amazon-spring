package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import com.jchristian.bot_amazon_spring.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionDetectionService {

	private static final String CHAVE_PERCENTUAL_MINIMO = "coleta.percentual-minimo-queda";
	private static final BigDecimal PERCENTUAL_MINIMO_PADRAO = new BigDecimal("10");
	private static final long MINIMO_ENTRADAS_HISTORICO_PARA_USAR_COMO_BASE = 2;

	private final ProductRepository productRepository;
	private final PriceHistoryRepository priceHistoryRepository;
	private final ConfigService configService;

	public List<CandidatoPromocaoDTO> buscarCandidatosElegiveis() {
		BigDecimal percentualMinimo = configService.getBigDecimal(CHAVE_PERCENTUAL_MINIMO, PERCENTUAL_MINIMO_PADRAO);

		List<CandidatoPromocaoDTO> candidatos = new ArrayList<>();
		for (Product product : productRepository.findAll()) {
			BigDecimal precoBase = calcularPrecoBase(product);
			if (precoBase == null) {
				continue;
			}

			BigDecimal percentualDesconto = calcularPercentualDesconto(precoBase, product.getPrecoAtual());
			boolean quedaRelevante = percentualDesconto.compareTo(percentualMinimo) >= 0;
			boolean jaSinalizadoNessePreco = product.getLastCandidatoPreco() != null
					&& product.getPrecoAtual().compareTo(product.getLastCandidatoPreco()) == 0;

			if (quedaRelevante && !jaSinalizadoNessePreco) {
				candidatos.add(new CandidatoPromocaoDTO(product, percentualDesconto));
				product.setLastCandidatoPreco(product.getPrecoAtual());
				productRepository.save(product);
			}
		}
		return candidatos;
	}

	private BigDecimal calcularPrecoBase(Product product) {
		long quantidadeHistorico = priceHistoryRepository.countByProduct(product);
		if (quantidadeHistorico >= MINIMO_ENTRADAS_HISTORICO_PARA_USAR_COMO_BASE) {
			return priceHistoryRepository.findMenorPrecoByProduct(product).orElse(null);
		}
		return product.getPrecoRiscado();
	}

	private BigDecimal calcularPercentualDesconto(BigDecimal precoBase, BigDecimal precoAtual) {
		return precoBase.subtract(precoAtual)
				.divide(precoBase, 4, RoundingMode.HALF_UP)
				.multiply(new BigDecimal("100"))
				.setScale(2, RoundingMode.HALF_UP);
	}

}
