package com.jchristian.bot_amazon_spring.scheduler;

import com.jchristian.bot_amazon_spring.service.ColetaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ColetaScheduler {

	private final ColetaService coletaService;

	@Scheduled(cron = "${coleta.cron}", zone = "America/Sao_Paulo")
	public void executarCicloAgendado() {
		log.info("COLETA: ciclo agendado iniciado");
		coletaService.executarCicloColeta();
		log.info("COLETA: ciclo agendado finalizado");
	}

}
