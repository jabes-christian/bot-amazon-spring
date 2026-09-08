package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.dto.CopyResultadoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnriquecimentoService {

	private final CopyGenerationService copyGenerationService;
	private final BannerImageService bannerImageService;

	public ConteudoEnriquecidoDTO enriquecer(CandidatoPromocaoDTO candidato) {
		CopyResultadoDTO copyResultado = copyGenerationService.gerarCopy(candidato);
		var bannerPath = bannerImageService.gerarBanner(candidato);
		return new ConteudoEnriquecidoDTO(copyResultado.texto(), bannerPath.orElse(null), copyResultado.viaLlm());
	}

}
