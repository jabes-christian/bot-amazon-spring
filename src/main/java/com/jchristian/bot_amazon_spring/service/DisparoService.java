package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.DispatchHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
import com.jchristian.bot_amazon_spring.repository.ChannelRepository;
import com.jchristian.bot_amazon_spring.repository.DispatchHistoryRepository;
import com.jchristian.bot_amazon_spring.service.sender.ChannelSender;
import com.jchristian.bot_amazon_spring.service.sender.EnvioException;
import com.jchristian.bot_amazon_spring.service.sender.TelegramChannelSender;
import com.jchristian.bot_amazon_spring.service.sender.WhatsAppChannelSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisparoService {

	private static final String CHAVE_TETO_PRODUTOS = "disparo.teto-produtos-por-canal";
	private static final String CHAVE_JANELA_DEDUP_DIAS = "disparo.janela-dedup-dias";
	private static final String CHAVE_INTERVALO_SEGUNDOS = "disparo.intervalo-entre-envios-segundos";

	private static final int TETO_PADRAO = 5;
	private static final long JANELA_DEDUP_PADRAO_DIAS = 7L;
	private static final long INTERVALO_PADRAO_SEGUNDOS = 2L;

	private final PromotionDetectionService promotionDetectionService;
	private final ChannelRepository channelRepository;
	private final DispatchHistoryRepository dispatchHistoryRepository;
	private final EnriquecimentoService enriquecimentoService;
	private final BannerImageService bannerImageService;
	private final TelegramChannelSender telegramChannelSender;
	private final WhatsAppChannelSender whatsAppChannelSender;
	private final ConfigService configService;

	public void executarCicloDisparo() {
		List<CandidatoPromocaoDTO> candidatos = promotionDetectionService.buscarCandidatosElegiveis();
		Map<Product, CandidatoPromocaoDTO> candidatoPorProduto =
				candidatos.stream().collect(Collectors.toMap(CandidatoPromocaoDTO::produto, c -> c));

		List<Channel> canaisAtivos = channelRepository.findByAtivoTrue();
		if (canaisAtivos.isEmpty()) {
			log.info("DISPARO: nenhum canal ativo, ciclo encerrado sem envio");
			return;
		}

		Map<Product, Set<Channel>> selecaoPorProduto = selecionarCandidatosPorCanal(canaisAtivos, candidatos);

		int copiasViaLlm = 0;
		int copiasViaTemplate = 0;

		for (Map.Entry<Product, Set<Channel>> entry : selecaoPorProduto.entrySet()) {
			Product produto = entry.getKey();
			Set<Channel> canaisDoProduto = entry.getValue();
			CandidatoPromocaoDTO candidato = candidatoPorProduto.get(produto);

			ConteudoEnriquecidoDTO conteudo = enriquecimentoService.enriquecer(candidato);
			if (conteudo.copyViaLlm()) {
				copiasViaLlm++;
			} else {
				copiasViaTemplate++;
			}

			for (Channel canal : canaisDoProduto) {
				enviarComRetry(canal, produto, conteudo);
			}

			if (conteudo.bannerPath() != null) {
				bannerImageService.removerBanner(conteudo.bannerPath());
			}
		}

		log.info("DISPARO: ciclo concluido - copiasViaLlm={}, copiasViaTemplate={}", copiasViaLlm, copiasViaTemplate);
	}

	private Map<Product, Set<Channel>> selecionarCandidatosPorCanal(List<Channel> canaisAtivos,
			List<CandidatoPromocaoDTO> candidatos) {
		int teto = configService.getInt(CHAVE_TETO_PRODUTOS, TETO_PADRAO);
		long janelaDias = configService.getLong(CHAVE_JANELA_DEDUP_DIAS, JANELA_DEDUP_PADRAO_DIAS);
		LocalDateTime desde = LocalDateTime.now().minusDays(janelaDias);

		Map<Product, Set<Channel>> selecaoPorProduto = new LinkedHashMap<>();

		for (Channel canal : canaisAtivos) {
			if (canal.getTipo() != TipoCanal.TELEGRAM && canal.getTipo() != TipoCanal.WHATSAPP) {
				log.warn("DISPARO: canal com tipo nao suportado, ignorado - canalId={}, tipo={}", canal.getId(),
						canal.getTipo());
				continue;
			}
			if (canal.getCategoriasAceitas() == null || canal.getCategoriasAceitas().isBlank()) {
				log.warn("DISPARO: canal com categorias aceitas vazias/nulas, ignorado - canalId={}", canal.getId());
				continue;
			}

			Set<String> categoriasAceitas = Set.of(canal.getCategoriasAceitas().split(","));
			List<CandidatoPromocaoDTO> elegiveisDoCanal = candidatos.stream()
					.filter(c -> categoriasAceitas.contains(c.produto().getCategoria().getCodigo()))
					.filter(c -> !dispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(c.produto(), canal,
							desde))
					.sorted(Comparator.comparing(CandidatoPromocaoDTO::percentualDesconto).reversed())
					.limit(teto)
					.toList();

			for (CandidatoPromocaoDTO candidato : elegiveisDoCanal) {
				selecaoPorProduto.computeIfAbsent(candidato.produto(), p -> new LinkedHashSet<>()).add(canal);
			}
		}

		return selecaoPorProduto;
	}

	private void enviarComRetry(Channel canal, Product produto, ConteudoEnriquecidoDTO conteudo) {
		ChannelSender sender = canal.getTipo() == TipoCanal.TELEGRAM ? telegramChannelSender : whatsAppChannelSender;

		try {
			sender.enviar(canal, conteudo);
			registrarSucesso(produto, canal);
		} catch (EnvioException primeiraFalha) {
			try {
				sender.enviar(canal, conteudo);
				registrarSucesso(produto, canal);
			} catch (EnvioException segundaFalha) {
				log.error("DISPARO: falha ao enviar apos retry - canal={}, produto={}, motivo={}", canal.getId(),
						produto.getAsin(), segundaFalha.getMessage());
			}
		} finally {
			aguardarIntervaloEntreEnvios();
		}
	}

	private void registrarSucesso(Product produto, Channel canal) {
		dispatchHistoryRepository.save(DispatchHistory.builder().product(produto).channel(canal).build());
	}

	private void aguardarIntervaloEntreEnvios() {
		long segundos = configService.getLong(CHAVE_INTERVALO_SEGUNDOS, INTERVALO_PADRAO_SEGUNDOS);
		try {
			Thread.sleep(Duration.ofSeconds(segundos));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
