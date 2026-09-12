package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.Channel;
import com.jchristian.bot_amazon_spring.entity.DispatchHistory;
import com.jchristian.bot_amazon_spring.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface DispatchHistoryRepository extends JpaRepository<DispatchHistory, Long> {

	boolean existsByProductAndChannelAndEnviadoEmAfter(Product product, Channel channel, LocalDateTime desde);

}
