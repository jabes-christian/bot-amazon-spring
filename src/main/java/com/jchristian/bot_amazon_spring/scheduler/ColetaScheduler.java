package com.jchristian.bot_amazon_spring.scheduler;

import com.jchristian.bot_amazon_spring.service.ColetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ColetaScheduler {

	private final ColetaService coletaService;

	@Scheduled(cron = "${coleta.cron}", zone = "America/Sao_Paulo")
	public void executarCicloAgendado() {
		coletaService.executarCicloColeta();
	}

}
