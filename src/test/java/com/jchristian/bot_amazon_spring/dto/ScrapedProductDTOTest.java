package com.jchristian.bot_amazon_spring.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScrapedProductDTOTest {

	@Test
	void construtorCompactoAceitaTodosOsCamposObrigatoriosPreenchidos() {
		ScrapedProductDTO dto = ScrapedProductDTO.of("B0EXEMPLO", "Monitor Gamer", new BigDecimal("999.90"),
				new BigDecimal("1299.90"), "https://img.example/foto.jpg", "https://amazon.com.br/dp/B0EXEMPLO");

		assertThat(dto.asin()).isEqualTo("B0EXEMPLO");
		assertThat(dto.titulo()).isEqualTo("Monitor Gamer");
		assertThat(dto.precoAtual()).isEqualByComparingTo("999.90");
		assertThat(dto.precoRiscado()).isEqualByComparingTo("1299.90");
	}

	@Test
	void lancaExcecaoQuandoAsinNuloOuVazio() {
		assertThrows(IllegalArgumentException.class,
				() -> ScrapedProductDTO.of(null, "Titulo", BigDecimal.TEN, null, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> ScrapedProductDTO.of("  ", "Titulo", BigDecimal.TEN, null, null, null));
	}

	@Test
	void lancaExcecaoQuandoTituloNuloOuVazio() {
		assertThrows(IllegalArgumentException.class,
				() -> ScrapedProductDTO.of("B0X", null, BigDecimal.TEN, null, null, null));
		assertThrows(IllegalArgumentException.class,
				() -> ScrapedProductDTO.of("B0X", " ", BigDecimal.TEN, null, null, null));
	}

	@Test
	void lancaExcecaoQuandoPrecoAtualNulo() {
		assertThrows(IllegalArgumentException.class,
				() -> ScrapedProductDTO.of("B0X", "Titulo", null, null, null, null));
	}

}
