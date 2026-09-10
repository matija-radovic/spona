package io.github.matijaradovic.spona;

import io.github.matijaradovic.spona.support.ContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
class SponaApplicationTests {

	@Test
	void contextLoads() {
	}

}
