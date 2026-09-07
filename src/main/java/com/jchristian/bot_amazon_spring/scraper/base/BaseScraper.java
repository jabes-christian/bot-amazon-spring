package com.jchristian.bot_amazon_spring.scraper.base;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

public abstract class BaseScraper {

	protected final WebDriver driver;
	protected final WebDriverWait wait;

	protected BaseScraper(WebDriver driver, WebDriverWait wait) {
		this.driver = driver;
		this.wait = wait;
	}

	protected void navegarPara(String url) {
		driver.get(url);
	}

	protected List<WebElement> aguardarElementos(By locator) {
		return wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator));
	}

	protected String extrairTexto(By locator) {
		return wait.until(ExpectedConditions.presenceOfElementLocated(locator)).getText();
	}

	protected String extrairAtributo(By locator, String atributo) {
		return wait.until(ExpectedConditions.presenceOfElementLocated(locator)).getAttribute(atributo);
	}

	protected boolean elementoExiste(By locator) {
		try {
			wait.until(ExpectedConditions.presenceOfElementLocated(locator));
			return true;
		} catch (TimeoutException | NoSuchElementException e) {
			return false;
		}
	}

}
