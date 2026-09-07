package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findByAsin(String asin);

}
