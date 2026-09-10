package io.github.matijaradovic.spona.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A throwaway PostgreSQL for tests. The tag matches compose.yaml */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

	// The container is Closeable and Spring owns its lifecycle, so the compiler's leak warning does not apply to a @Bean method.
	@SuppressWarnings("resource")
	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
				.withReuse(true);
	}
}
