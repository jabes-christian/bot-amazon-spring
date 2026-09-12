package com.jchristian.bot_amazon_spring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(optional = false)
	@JoinColumn(name = "channel_id", nullable = false)
	private Channel channel;

	@Column(name = "enviado_em", nullable = false)
	private LocalDateTime enviadoEm;

	@PrePersist
	protected void onCreate() {
		this.enviadoEm = LocalDateTime.now();
	}

}
