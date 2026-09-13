package com.jchristian.bot_amazon_spring.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.service.ColetaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ColetaSchedulerTest {

	@Mock
	private ColetaService coletaService;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(ColetaScheduler.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(ColetaScheduler.class);
		logger.detachAppender(logAppender);
	}

	@Test
	void executarCicloAgendadoChamaColetaServiceELogaInicioEFimPrefixadosColeta() {
		ColetaScheduler scheduler = new ColetaScheduler(coletaService);

		scheduler.executarCicloAgendado();

		verify(coletaService, times(1)).executarCicloColeta();
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.INFO
				&& evento.getFormattedMessage().equals("COLETA: ciclo agendado iniciado"));
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.INFO
				&& evento.getFormattedMessage().equals("COLETA: ciclo agendado finalizado"));
	}

}
