package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.PriceHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

	long countByProduct(Product product);

	@Query("SELECT MIN(ph.preco) FROM PriceHistory ph WHERE ph.product = :product")
	Optional<BigDecimal> findMenorPrecoByProduct(@Param("product") Product product);

	@Query("SELECT MIN(ph.preco) FROM PriceHistory ph WHERE ph.product = :product AND ph.capturadoEm >= :desde")
	Optional<BigDecimal> findMenorPrecoDesde(@Param("product") Product product, @Param("desde") LocalDateTime desde);

}
