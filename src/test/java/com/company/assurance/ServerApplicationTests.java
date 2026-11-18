package com.company.assurance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationTests {

	@Test
	void contextLoads() {
		// Verifies the application does not require a default datasource.
	}

}
