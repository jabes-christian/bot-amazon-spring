package com.jchristian.bot_amazon_spring.dto;

import com.jchristian.bot_amazon_spring.entity.Product;

import java.math.BigDecimal;

public record CandidatoPromocaoDTO(Product produto, BigDecimal percentualDesconto) {
}
