package com.jchristian.bot_amazon_spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String asin;

	@ManyToOne(optional = false)
	@JoinColumn(name = "categoria_id", nullable = false)
	private CategoriaColeta categoria;

	@Column(nullable = false)
	private String titulo;

	@Column(name = "preco_atual", nullable = false)
	private BigDecimal precoAtual;

	@Column(name = "preco_riscado")
	private BigDecimal precoRiscado;

	@Column(name = "url_imagem")
	private String urlImagem;

	@Column(name = "url_produto")
	private String urlProduto;

	@Column(name = "last_candidato_preco")
	private BigDecimal lastCandidatoPreco;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.criadoEm = now;
		this.atualizadoEm = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.atualizadoEm = LocalDateTime.now();
	}

}
