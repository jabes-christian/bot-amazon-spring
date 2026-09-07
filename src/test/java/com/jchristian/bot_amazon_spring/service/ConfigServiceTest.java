package com.jchristian.bot_amazon_spring.service;

import com.jchristian.bot_amazon_spring.entity.AppConfig;
import com.jchristian.bot_amazon_spring.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

	@Mock
	private AppConfigRepository appConfigRepository;

	@InjectMocks
	private ConfigService configService;

	@Test
	void getBigDecimalRetornaValorConvertidoQuandoChaveExiste() {
		when(appConfigRepository.findByChave("coleta.percentual-minimo-queda"))
				.thenReturn(Optional.of(AppConfig.builder().chave("coleta.percentual-minimo-queda").valor("10").build()));

		BigDecimal resultado = configService.getBigDecimal("coleta.percentual-minimo-queda", BigDecimal.ONE);

		assertThat(resultado).isEqualByComparingTo("10");
	}

	@Test
	void getBigDecimalRetornaPadraoQuandoChaveAusente() {
		when(appConfigRepository.findByChave("chave.inexistente")).thenReturn(Optional.empty());

		BigDecimal resultado = configService.getBigDecimal("chave.inexistente", BigDecimal.TEN);

		assertThat(resultado).isEqualByComparingTo(BigDecimal.TEN);
	}

	@Test
	void getIntRetornaValorConvertidoQuandoChaveExiste() {
		when(appConfigRepository.findByChave("disparo.teto-produtos-por-canal"))
				.thenReturn(Optional.of(AppConfig.builder().chave("disparo.teto-produtos-por-canal").valor("5").build()));

		int resultado = configService.getInt("disparo.teto-produtos-por-canal", 1);

		assertThat(resultado).isEqualTo(5);
	}

	@Test
	void getIntRetornaPadraoQuandoChaveAusente() {
		when(appConfigRepository.findByChave("chave.inexistente")).thenReturn(Optional.empty());

		int resultado = configService.getInt("chave.inexistente", 7);

		assertThat(resultado).isEqualTo(7);
	}

	@Test
	void getLongRetornaValorConvertidoQuandoChaveExiste() {
		when(appConfigRepository.findByChave("disparo.janela-dedup-dias"))
				.thenReturn(Optional.of(AppConfig.builder().chave("disparo.janela-dedup-dias").valor("7").build()));

		long resultado = configService.getLong("disparo.janela-dedup-dias", 1L);

		assertThat(resultado).isEqualTo(7L);
	}

	@Test
	void getLongRetornaPadraoQuandoChaveAusente() {
		when(appConfigRepository.findByChave("chave.inexistente")).thenReturn(Optional.empty());

		long resultado = configService.getLong("chave.inexistente", 42L);

		assertThat(resultado).isEqualTo(42L);
	}

}
