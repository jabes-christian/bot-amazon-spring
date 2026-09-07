package com.jchristian.bot_amazon_spring.repository;

import com.jchristian.bot_amazon_spring.entity.CategoriaColeta;
import com.jchristian.bot_amazon_spring.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductRepositoryIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CategoriaColetaRepository categoriaColetaRepository;

	private CategoriaColeta categoria;

	@BeforeEach
	void setUp() {
		List<CategoriaColeta> categorias = categoriaColetaRepository.findByAtivoTrue();
		categoria = categorias.get(0);
	}

	@Test
	void salvaEBuscaPorAsinExistente() {
		Product product = Product.builder()
				.asin("B0TESTE001")
				.categoria(categoria)
				.titulo("Produto de teste")
				.precoAtual(new BigDecimal("199.90"))
				.build();
		productRepository.saveAndFlush(product);

		Optional<Product> encontrado = productRepository.findByAsin("B0TESTE001");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getTitulo()).isEqualTo("Produto de teste");
	}

	@Test
	void buscaPorAsinInexistenteRetornaVazio() {
		Optional<Product> encontrado = productRepository.findByAsin("ASIN_QUE_NAO_EXISTE");

		assertThat(encontrado).isEmpty();
	}

	@Test
	void inserirAsinDuplicadoViolaConstraintUnica() {
		productRepository.saveAndFlush(Product.builder()
				.asin("B0TESTE002")
				.categoria(categoria)
				.titulo("Produto original")
				.precoAtual(new BigDecimal("50.00"))
				.build());

		Product duplicado = Product.builder()
				.asin("B0TESTE002")
				.categoria(categoria)
				.titulo("Produto duplicado")
				.precoAtual(new BigDecimal("60.00"))
				.build();

		assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(duplicado));
	}

}
