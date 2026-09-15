package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.dto.CopyResultadoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CopyGenerationService {

	private static final String CHAVE_TIMEOUT_LLM = "enriquecimento.llm-timeout-segundos";
	private static final String CHAVE_LIMITE_CARACTERES = "enriquecimento.limite-caracteres-copy";
	private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
	private static final String SEPARADOR_LINK = "\n\n";

	private final ChatModel chatModel;
	private final ConfigService configService;
	private final String tagAfiliado;
	private final ExecutorService llmExecutor = Executors.newVirtualThreadPerTaskExecutor();

	public CopyGenerationService(ChatModel chatModel, ConfigService configService,
			@Value("${afiliado.tag:}") String tagAfiliado) {
		this.chatModel = chatModel;
		this.configService = configService;
		this.tagAfiliado = tagAfiliado;
	}

	public CopyResultadoDTO gerarCopy(CandidatoPromocaoDTO candidato) {
		long timeoutSegundos = configService.getLong(CHAVE_TIMEOUT_LLM, 15);
		String prompt = montarPrompt(candidato);

		// Executor nunca é fechado por esta chamada (nem via try-with-resources): ExecutorService.close()
		// bloqueia a thread chamadora ate a tarefa em andamento terminar, o que anularia o proprio timeout
		// se a chamada ao LLM travar. Threads virtuais sao daemon por definicao, entao nao ha vazamento
		// que impeca o shutdown da JVM.
		CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> chatModel.chat(prompt), llmExecutor);
		try {
			String textoLlm = future.get(timeoutSegundos, TimeUnit.SECONDS);
			if (textoLlm == null || textoLlm.isBlank()) {
				throw new IllegalStateException("resposta vazia do LLM");
			}
			return montarResultado(textoLlm, candidato, true);
		} catch (TimeoutException | ExecutionException | InterruptedException | IllegalStateException e) {
			future.cancel(true);
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("ENRIQUECIMENTO: falha ao gerar copy via LLM, usando fallback de template - asin={}, motivo={}",
					candidato.produto().getAsin(), e.getMessage());
			return montarResultado(montarCopyTemplate(candidato), candidato, false);
		}
	}

	private String montarPrompt(CandidatoPromocaoDTO candidato) {
		Product produto = candidato.produto();
		return """
				Escreva uma copy publicitária breve em português do Brasil para divulgar a oferta abaixo em um canal de promoções.
				Produto: %s
				Preço anterior: R$ %s
				Preço atual: R$ %s
				Desconto: %s%%
				Inclua o nome do produto, os dois preços, o percentual de desconto e uma chamada para ação.
				Não inclua nenhum link ou URL no texto.
				Apresente o preço anterior em tachado usando Markdown padrão (~~preço anterior~~) e o preço atual em destaque usando negrito Markdown padrão (**preço atual**).
				Não inclua nenhuma tag, marcador ou texto de controle (como <CPA_DONE> ou similares) - a resposta deve conter apenas a copy em texto puro.
				""".formatted(produto.getTitulo(), candidato.precoBase(), produto.getPrecoAtual(),
				candidato.percentualDesconto());
	}

	private String montarCopyTemplate(CandidatoPromocaoDTO candidato) {
		Product produto = candidato.produto();
		return "%s\nDe: R$ %s / Por: R$ %s (-%s%%)".formatted(produto.getTitulo(), candidato.precoBase(),
				produto.getPrecoAtual(), candidato.percentualDesconto());
	}

	private CopyResultadoDTO montarResultado(String corpo, CandidatoPromocaoDTO candidato, boolean viaLlm) {
		String corpoSemLink = URL_PATTERN.matcher(corpo).replaceAll("").strip();
		String link = montarLinkAfiliado(candidato.produto());
		int limite = configService.getInt(CHAVE_LIMITE_CARACTERES, 1024);
		String corpoTruncado = truncarCorpo(corpoSemLink, link, limite);
		String textoFinal = corpoTruncado.isBlank() ? link : corpoTruncado + SEPARADOR_LINK + link;
		return new CopyResultadoDTO(textoFinal, viaLlm);
	}

	private String montarLinkAfiliado(Product produto) {
		String urlProduto = produto.getUrlProduto();
		String separador = urlProduto.contains("?") ? "&tag=" : "?tag=";
		return urlProduto + separador + tagAfiliado;
	}

	private String truncarCorpo(String corpo, String link, int limite) {
		int espacoParaCorpo = Math.max(0, limite - link.length() - SEPARADOR_LINK.length());
		if (corpo.length() <= espacoParaCorpo) {
			return corpo;
		}
		return corpo.substring(0, espacoParaCorpo).stripTrailing();
	}

}
