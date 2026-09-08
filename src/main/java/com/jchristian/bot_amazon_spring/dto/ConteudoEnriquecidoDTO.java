package com.jchristian.bot_amazon_spring.dto;

import java.nio.file.Path;

public record ConteudoEnriquecidoDTO(String copy, Path bannerPath, boolean copyViaLlm) {
}
