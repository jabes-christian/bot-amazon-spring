package com.jchristian.bot_amazon_spring.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.time.Duration;

@Configuration
public class SeleniumConfig {

	@Bean
	public ChromeOptions chromeOptions() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments(
				"--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
		return options;
	}

	@Bean
	public WebDriver webDriver(ChromeOptions chromeOptions, @Value("${selenium.remote.url:}") String remoteUrl)
			throws URISyntaxException, MalformedURLException {
		if (!remoteUrl.isBlank()) {
			return new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), chromeOptions);
		}
		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(chromeOptions);
	}

	@Bean
	public WebDriverWait webDriverWait(WebDriver webDriver,
			@Value("${selenium.wait-timeout-segundos:10}") long timeoutSegundos) {
		return new WebDriverWait(webDriver, Duration.ofSeconds(timeoutSegundos));
	}

}
