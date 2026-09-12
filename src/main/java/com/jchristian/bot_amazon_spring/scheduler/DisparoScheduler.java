package com.jchristian.bot_amazon_spring.scheduler;

import com.jchristian.bot_amazon_spring.service.DisparoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class DisparoScheduler {

	private final DisparoService disparoService;
	private final AtomicBoolean emExecucao = new AtomicBoolean(false);

	public DisparoScheduler(DisparoService disparoService) {
		this.disparoService = disparoService;
	}

	@Scheduled(cron = "${disparo.cron}", zone = "America/Sao_Paulo")
	public void executarCicloAgendado() {
		if (!emExecucao.compareAndSet(false, true)) {
			log.warn("DISPARO: ciclo de disparo anterior ainda em execucao, pulando esta execucao agendada");
			return;
		}
		try {
			disparoService.executarCicloDisparo();
		} finally {
			emExecucao.set(false);
		}
	}

}
