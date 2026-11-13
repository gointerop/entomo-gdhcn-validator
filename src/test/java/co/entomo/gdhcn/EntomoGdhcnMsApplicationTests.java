package co.entomo.gdhcn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.entomo.gdhcn.service.GdhcnService;
import nl.minvws.encoding.Base45;

@SpringBootTest
@ActiveProfiles({ "test", "fileSystem" })
class EntomoGdhcnMsApplicationTests {

	/** The real bean loads mTLS key material from disk; tests must not depend on it. */
	@MockitoBean
	HttpClient httpClient;

	@Autowired
	GdhcnService gdhcnService;

	@Test
	void contextLoads() {
		assertNotNull(gdhcnService);
	}

	@Test
	void base45RoundTrip() {
		byte[] data = "shlink://ababcbc".getBytes(StandardCharsets.UTF_8);
		byte[] encoded = Base45.getEncoder().encode(data);
		assertArrayEquals(data, Base45.getDecoder().decode(encoded));
	}
}
