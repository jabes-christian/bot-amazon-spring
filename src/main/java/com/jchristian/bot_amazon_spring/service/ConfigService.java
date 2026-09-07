package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.entity.AppConfig;
import com.jchristian.bot_amazon_spring.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

	private final AppConfigRepository appConfigRepository;

	public BigDecimal getBigDecimal(String chave, BigDecimal valorPadrao) {
		return buscarValor(chave).map(BigDecimal::new).orElseGet(() -> valorAusente(chave, valorPadrao));
	}

	public int getInt(String chave, int valorPadrao) {
		return buscarValor(chave).map(Integer::parseInt).orElseGet(() -> valorAusente(chave, valorPadrao));
	}

	public long getLong(String chave, long valorPadrao) {
		return buscarValor(chave).map(Long::parseLong).orElseGet(() -> valorAusente(chave, valorPadrao));
	}

	private Optional<String> buscarValor(String chave) {
		return appConfigRepository.findByChave(chave).map(AppConfig::getValor);
	}

	private <T> T valorAusente(String chave, T valorPadrao) {
		log.warn("chave de config ausente, usando padrao: {} -> {}", chave, valorPadrao);
		return valorPadrao;
	}

}
