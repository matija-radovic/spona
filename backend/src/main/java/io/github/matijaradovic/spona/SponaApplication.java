package io.github.matijaradovic.spona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SponaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SponaApplication.class, args);
	}

}
