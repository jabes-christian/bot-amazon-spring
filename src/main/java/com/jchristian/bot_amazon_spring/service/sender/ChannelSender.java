package com.jchristian.bot_amazon_spring.service.sender;

import com.jchristian.bot_amazon_spring.dto.ConteudoEnriquecidoDTO;
import com.jchristian.bot_amazon_spring.entity.Channel;

public interface ChannelSender {

	void enviar(Channel canal, ConteudoEnriquecidoDTO conteudo) throws EnvioException;

}
