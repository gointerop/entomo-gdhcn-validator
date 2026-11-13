package co.entomo.gdhcn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.entomo.gdhcn.service.GdhcnFileSystem;
import co.entomo.gdhcn.service.impl.AwsFileSystem;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** The "aws" profile against an S3-compatible server (Adobe S3Mock); skipped when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({ "test", "aws" })
class S3StorageTests {

	static final String BUCKET = "gdhcn-ips";

	@Container
	static GenericContainer<?> s3 = new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(9090)
			.withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
			.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("aws.bucket.endpoint", () -> "http://" + s3.getHost() + ":" + s3.getMappedPort(9090));
		r.add("aws.bucket.access.key", () -> "test");
		r.add("aws.bucket.access.secret", () -> "test");
		r.add("aws.bucket.s3.bucket.name", () -> BUCKET);
	}

	@MockitoBean
	HttpClient httpClient;

	@Autowired
	GdhcnFileSystem fileSystem;

	@Test
	void awsProfileWiresTheS3FileSystem() {
		assertInstanceOf(AwsFileSystem.class, fileSystem);
	}

	@Test
	void stringUploadCanBeDownloaded() throws Exception {
		fileSystem.uploadJson("a.json", "{\"résumé\":\"ü\"}");
		try (InputStream in = fileSystem.downloadJson("a.json")) {
			assertEquals("{\"résumé\":\"ü\"}", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void fileUploadCanBeDownloaded() throws Exception {
		Path tmp = Files.createTempFile("gdhcn", ".json");
		Files.writeString(tmp, "{\"from\":\"file\"}");
		fileSystem.uploadJson("b.json", tmp.toFile());
		try (InputStream in = fileSystem.downloadJson("b.json")) {
			assertEquals("{\"from\":\"file\"}", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void missingObjectFailsWithNoSuchKey() {
		assertThrows(NoSuchKeyException.class, () -> fileSystem.downloadJson("does-not-exist.json"));
	}

	@Test
	void manyDownloadsDoNotExhaustTheConnectionPool() throws Exception {
		fileSystem.uploadJson("pool.json", "{}");
		// the default Apache pool allows 50 connections; unclosed streams would block well before 120
		for (int i = 0; i < 120; i++) {
			try (InputStream in = fileSystem.downloadJson("pool.json")) {
				in.readAllBytes();
			}
		}
	}
}
