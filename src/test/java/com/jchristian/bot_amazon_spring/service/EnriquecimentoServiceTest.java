package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.dto.CopyResultadoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnriquecimentoServiceTest {

	@Mock
	private CopyGenerationService copyGenerationService;

	@Mock
	private BannerImageService bannerImageService;

	private EnriquecimentoService novoService() {
		return new EnriquecimentoService(copyGenerationService, bannerImageService);
	}

	private static CandidatoPromocaoDTO candidato() {
		Product produto = Product.builder().id(1L).asin("B0ENRICH1").titulo("Produto").precoAtual(new BigDecimal("100.00")).build();
		return new CandidatoPromocaoDTO(produto, new BigDecimal("20.00"), new BigDecimal("125.00"));
	}

	@Test
	void enriquecerChamaGerarCopyEGerarBannerExatamenteUmaVezCada() {
		CandidatoPromocaoDTO candidato = candidato();
		when(copyGenerationService.gerarCopy(candidato)).thenReturn(new CopyResultadoDTO("copy gerada", true));
		when(bannerImageService.gerarBanner(candidato)).thenReturn(Optional.of(Path.of("/tmp/banner.jpg")));

		novoService().enriquecer(candidato);

		verify(copyGenerationService, times(1)).gerarCopy(candidato);
		verify(bannerImageService, times(1)).gerarBanner(candidato);
	}

	@Test
	void resultadoMontaCopyBannerPathECopyViaLlmDosServicesInternos() {
		CandidatoPromocaoDTO candidato = candidato();
		when(copyGenerationService.gerarCopy(candidato)).thenReturn(new CopyResultadoDTO("copy gerada via LLM", true));
		when(bannerImageService.gerarBanner(candidato)).thenReturn(Optional.of(Path.of("/tmp/banner-B0ENRICH1.jpg")));

		ConteudoEnriquecidoDTO resultado = novoService().enriquecer(candidato);

		assertThat(resultado.copy()).isEqualTo("copy gerada via LLM");
		assertThat(resultado.bannerPath()).isEqualTo(Path.of("/tmp/banner-B0ENRICH1.jpg"));
		assertThat(resultado.copyViaLlm()).isTrue();
	}

	@Test
	void bannerAusenteResultaEmBannerPathNuloPreservandoACopyCompleta() {
		CandidatoPromocaoDTO candidato = candidato();
		when(copyGenerationService.gerarCopy(candidato))
				.thenReturn(new CopyResultadoDTO("copy completa sem banner", false));
		when(bannerImageService.gerarBanner(candidato)).thenReturn(Optional.empty());

		ConteudoEnriquecidoDTO resultado = novoService().enriquecer(candidato);

		assertThat(resultado.bannerPath()).isNull();
		assertThat(resultado.copy()).isEqualTo("copy completa sem banner");
		assertThat(resultado.copyViaLlm()).isFalse();
	}

}
