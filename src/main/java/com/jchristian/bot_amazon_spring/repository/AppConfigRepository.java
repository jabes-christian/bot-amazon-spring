package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

	Optional<AppConfig> findByChave(String chave);

}
