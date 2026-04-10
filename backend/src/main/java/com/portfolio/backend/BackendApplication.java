package com.portfolio.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@SpringBootApplication
public class BackendApplication {

	@Value("${spring.security.oauth2.client.registration.github.client-secret:NOT_SET}")
	private String githubClientSecret;

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		boolean secretLoaded = githubClientSecret != null
				&& !githubClientSecret.equals("NOT_SET")
				&& !githubClientSecret.isBlank();
		log.info("GITHUB_CLIENT_SECRET loaded: {} (length: {})",
				secretLoaded,
				secretLoaded ? githubClientSecret.length() : 0);
	}
}
