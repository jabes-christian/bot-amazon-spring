package com.jchristian.bot_amazon_spring.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jchristian.bot_amazon_spring.service.DisparoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisparoSchedulerTest {

	@Mock
	private DisparoService disparoService;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUpLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(DisparoScheduler.class);
		logAppender = new ListAppender<>();
		logAppender.start();
		logger.addAppender(logAppender);
	}

	@AfterEach
	void tearDownLogCapture() {
		Logger logger = (Logger) LoggerFactory.getLogger(DisparoScheduler.class);
		logger.detachAppender(logAppender);
	}

	@Test
	void execucaoConcorrenteEhPuladaComWarnEDisparoServiceChamadoApenasUmaVez() {
		DisparoScheduler scheduler = new DisparoScheduler(disparoService);
		doAnswer(invocation -> {
			scheduler.executarCicloAgendado();
			return null;
		}).when(disparoService).executarCicloDisparo();

		scheduler.executarCicloAgendado();

		verify(disparoService, times(1)).executarCicloDisparo();
		assertThat(logAppender.list).anyMatch(evento -> evento.getLevel() == Level.WARN
				&& evento.getFormattedMessage().contains("pulando esta execucao agendada"));
	}

	@Test
	void flagEhLiberadaAposExecucaoBemSucedidaPermitindoProximaExecucao() {
		DisparoScheduler scheduler = new DisparoScheduler(disparoService);

		scheduler.executarCicloAgendado();
		scheduler.executarCicloAgendado();

		verify(disparoService, times(2)).executarCicloDisparo();
	}

	@Test
	void flagEhLiberadaMesmoAposExcecaoPermitindoProximaExecucao() {
		DisparoScheduler scheduler = new DisparoScheduler(disparoService);
		doThrow(new RuntimeException("falha simulada")).doNothing().when(disparoService).executarCicloDisparo();

		assertThatThrownBy(scheduler::executarCicloAgendado).isInstanceOf(RuntimeException.class);
		scheduler.executarCicloAgendado();

		verify(disparoService, times(2)).executarCicloDisparo();
	}

}
