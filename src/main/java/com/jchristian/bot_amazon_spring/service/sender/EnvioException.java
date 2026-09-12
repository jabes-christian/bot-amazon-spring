package com.jchristian.bot_amazon_spring.service.sender;

public class EnvioException extends RuntimeException {

	public EnvioException(String message) {
		super(message);
	}

	public EnvioException(String message, Throwable cause) {
		super(message, cause);
	}

}
