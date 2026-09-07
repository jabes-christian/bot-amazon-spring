package com.jchristian.bot_amazon_spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String chave;

	@Column(nullable = false)
	private String valor;

	private String descricao;

	private LocalDateTime atualizadoEm;

	@PreUpdate
	protected void onUpdate() {
		this.atualizadoEm = LocalDateTime.now();
	}

}
