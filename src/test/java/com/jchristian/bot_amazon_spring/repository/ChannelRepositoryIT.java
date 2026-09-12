package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.TipoCanal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ChannelRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private ChannelRepository channelRepository;

	@Test
	void findByAtivoTrueRetornaApenasCanaisAtivos() {
		Channel ativo = channelRepository.saveAndFlush(Channel.builder()
				.tipo(TipoCanal.TELEGRAM)
				.identificador("-100123")
				.categoriasAceitas("MONITOR,NOTEBOOK")
				.ativo(true)
				.build());
		channelRepository.saveAndFlush(Channel.builder()
				.tipo(TipoCanal.WHATSAPP)
				.identificador("5511999999999")
				.categoriasAceitas("MONITOR")
				.ativo(false)
				.build());

		List<Channel> ativos = channelRepository.findByAtivoTrue();

		assertThat(ativos).extracting(Channel::getId).containsExactly(ativo.getId());
	}

}
