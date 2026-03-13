package com.example.scalableurlshortener;

import com.example.scalableurlshortener.config.RateLimitProperties;
import com.example.scalableurlshortener.config.UrlValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({UrlValidationProperties.class, RateLimitProperties.class})
public class ScalableUrlShortenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScalableUrlShortenerApplication.class, args);
	}

}
