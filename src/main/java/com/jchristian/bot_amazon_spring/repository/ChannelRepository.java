package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

	List<Channel> findByAtivoTrue();

}
