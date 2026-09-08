package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.dto.CandidatoPromocaoDTO;
import com.jchristian.bot_amazon_spring.entity.Product;
import com.jchristian.bot_amazon_spring.repository.PriceHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Optional;

@Service
@Slf4j
public class BannerImageService {

	private static final String CHAVE_SELO_DIAS = "enriquecimento.selo-menor-preco-dias";
	private static final int TAMANHO_CANVAS = 800;

	private final RestClient restClient;
	private final ConfigService configService;
	private final PriceHistoryRepository priceHistoryRepository;

	public BannerImageService(RestClient.Builder restClientBuilder, ConfigService configService,
			PriceHistoryRepository priceHistoryRepository) {
		this.restClient = restClientBuilder.build();
		this.configService = configService;
		this.priceHistoryRepository = priceHistoryRepository;
	}

	public Optional<Path> gerarBanner(CandidatoPromocaoDTO candidato) {
		Product produto = candidato.produto();

		byte[] bytesImagem;
		try {
			bytesImagem = restClient.get().uri(produto.getUrlImagem()).retrieve().body(byte[].class);
		} catch (RestClientException e) {
			log.warn("ENRIQUECIMENTO: falha ao baixar imagem do produto, banner nao gerado - asin={}, motivo={}",
					produto.getAsin(), e.getMessage());
			return Optional.empty();
		}

		BufferedImage imagemProduto = decodificarImagem(bytesImagem);
		if (imagemProduto == null) {
			log.warn(
					"ENRIQUECIMENTO: conteudo baixado nao e uma imagem valida, banner nao gerado - asin={}",
					produto.getAsin());
			return Optional.empty();
		}

		BufferedImage banner = compor(imagemProduto, candidato, deveIncluirSeloMenorPreco(candidato));

		try {
			Path arquivo = Files.createTempFile("banner-" + produto.getAsin() + "-", ".jpg");
			try (OutputStream out = Files.newOutputStream(arquivo)) {
				escreverJpeg(banner, out);
			}
			return Optional.of(arquivo);
		} catch (IOException e) {
			log.warn("ENRIQUECIMENTO: falha ao gravar banner em disco, banner nao gerado - asin={}, motivo={}",
					produto.getAsin(), e.getMessage());
			return Optional.empty();
		}
	}

	public void removerBanner(Path bannerPath) {
		try {
			Files.deleteIfExists(bannerPath);
		} catch (IOException e) {
			log.warn("ENRIQUECIMENTO: falha ao remover arquivo de banner temporario - path={}, motivo={}", bannerPath,
					e.getMessage());
		}
	}

	private BufferedImage decodificarImagem(byte[] bytesImagem) {
		if (bytesImagem == null || bytesImagem.length == 0) {
			return null;
		}
		try {
			return ImageIO.read(new ByteArrayInputStream(bytesImagem));
		} catch (IOException e) {
			return null;
		}
	}

	private boolean deveIncluirSeloMenorPreco(CandidatoPromocaoDTO candidato) {
		Product produto = candidato.produto();
		int diasSelo = configService.getInt(CHAVE_SELO_DIAS, 90);
		LocalDateTime desde = LocalDateTime.now().minusDays(diasSelo);
		return priceHistoryRepository.findMenorPrecoDesde(produto, desde)
				.map(menorPreco -> produto.getPrecoAtual().compareTo(menorPreco) <= 0)
				.orElse(false);
	}

	private BufferedImage compor(BufferedImage imagemProduto, CandidatoPromocaoDTO candidato,
			boolean incluirSeloMenorPreco) {
		Product produto = candidato.produto();
		BufferedImage canvas = new BufferedImage(TAMANHO_CANVAS, TAMANHO_CANVAS, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(imagemProduto, 0, 0, TAMANHO_CANVAS, TAMANHO_CANVAS, null);

			String textoDesconto = "-" + candidato.percentualDesconto().stripTrailingZeros().toPlainString() + "%";
			String textoDePor = "De: R$ " + candidato.precoBase() + " / Por: R$ " + produto.getPrecoAtual();

			g.setColor(new Color(0, 0, 0, 160));
			g.fillRect(0, TAMANHO_CANVAS - 120, TAMANHO_CANVAS, 120);
			g.setColor(Color.WHITE);
			g.setFont(new Font("SansSerif", Font.BOLD, 36));
			g.drawString(textoDesconto, 20, TAMANHO_CANVAS - 70);
			g.setFont(new Font("SansSerif", Font.PLAIN, 24));
			g.drawString(textoDePor, 20, TAMANHO_CANVAS - 30);

			if (incluirSeloMenorPreco) {
				int diasSelo = configService.getInt(CHAVE_SELO_DIAS, 90);
				g.setColor(new Color(220, 20, 20));
				g.fillRect(0, 0, TAMANHO_CANVAS, 50);
				g.setColor(Color.WHITE);
				g.setFont(new Font("SansSerif", Font.BOLD, 22));
				g.drawString("MENOR PRECO EM " + diasSelo + " DIAS", 20, 34);
			}
		} finally {
			g.dispose();
		}
		return canvas;
	}

	private void escreverJpeg(BufferedImage imagem, OutputStream out) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		ImageWriter writer = writers.next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(0.85f);
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(imagem, null, null), param);
		} finally {
			writer.dispose();
		}
	}

}
