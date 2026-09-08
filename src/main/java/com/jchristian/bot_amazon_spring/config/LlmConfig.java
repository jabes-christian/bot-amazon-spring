package com.jchristian.bot_amazon_spring.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

	@Bean
	public ChatModel chatModel(@Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
			@Value("${openrouter.api-key:}") String apiKey, @Value("${openrouter.model:}") String modelName) {
		return OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).modelName(modelName).build();
	}

}
