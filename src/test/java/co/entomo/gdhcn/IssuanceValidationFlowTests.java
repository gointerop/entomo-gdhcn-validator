package co.entomo.gdhcn;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end issue -> validate flow through the REST API. Only the WHO TNG trust-list call is
 * mocked (it returns a certificate generated here); everything else is the real code path:
 * JSON binding, ModelMapper, JPA, file storage, CBOR/COSE/deflate/Base45 and signature checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "fileSystem" })
class IssuanceValidationFlowTests {

	static final String KID = "qb4L0atiX/4=";
	static final String COUNTRY = "XM";
	static final Path DIR;
	static final String CERTIFICATE_BASE64;

	static {
		try {
			DIR = Files.createTempDirectory("gdhcn-it");
			Files.createDirectories(DIR.resolve("json"));
			KeyPair kp = newKeyPair();
			CERTIFICATE_BASE64 = Base64.getEncoder().encodeToString(selfSigned(kp, "GDHCN Test DSC").getEncoded());
			String pem = "-----BEGIN PRIVATE KEY-----\n"
					+ Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
							.encodeToString(kp.getPrivate().getEncoded())
					+ "\n-----END PRIVATE KEY-----\n";
			Files.writeString(DIR.resolve("dsc.key"), pem);
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static KeyPair newKeyPair() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		return kpg.generateKeyPair();
	}

	static X509Certificate selfSigned(KeyPair kp, String cn) throws Exception {
		X500Name name = new X500Name("CN=" + cn + ",C=" + COUNTRY);
		Date from = new Date(System.currentTimeMillis() - 86_400_000L);
		Date to = new Date(System.currentTimeMillis() + 86_400_000L * 365);
		return new JcaX509CertificateConverter().getCertificate(
				new JcaX509v3CertificateBuilder(name, BigInteger.ONE, from, to, name, kp.getPublic())
						.build(new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate())));
	}

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("filesystem.baseLocation", DIR::toString);
		r.add("tng.dsc.privateKey", () -> DIR.resolve("dsc.key").toString());
		r.add("tng.dsc.privateKey.kid", () -> KID);
		r.add("tng.country", () -> COUNTRY);
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	HttpClient httpClient;

	@BeforeEach
	void publishTrustedCertificate() throws Exception {
		stubTrustList(CERTIFICATE_BASE64);
	}

	@SuppressWarnings("unchecked")
	private void stubTrustList(String certificate) throws Exception {
		HttpResponse<String> resp = mock(HttpResponse.class);
		when(resp.statusCode()).thenReturn(200);
		when(resp.body()).thenReturn("[{\"uuid\":\"u1\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"country\":\""
				+ COUNTRY + "\",\"kid\":\"" + KID + "\",\"certificateType\":\"DSC\",\"version\":1,"
				+ "\"domain\":\"DCC\",\"certificate\":\"" + certificate + "\",\"group\":\"DSC\","
				+ "\"unknownFutureField\":true}]");
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);
	}

	private String issue(String expiresOn) throws Exception {
		String body = "{\"jsonContent\":\"{\\\"resourceType\\\":\\\"Bundle\\\"}\""
				+ (expiresOn != null ? ",\"expiresOn\":\"" + expiresOn + "\"" : "") + "}";
		String hc1 = mvc.perform(post("/v2/vshcIssuance").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(hc1.startsWith("HC1:"), "unexpected issuance output: " + hc1);
		return hc1;
	}

	private ResultActions validate(String hc1) throws Exception {
		return mvc.perform(post("/v2/vshcValidation").contentType(MediaType.APPLICATION_JSON)
				.content("{\"qrCodeContent\":\"" + hc1 + "\"}"));
	}

	@Test
	void issuedCertificateValidatesThroughAllNineSteps() throws Exception {
		ResultActions r = validate(issue("2099-01-01T00:00:00.000Z")).andExpect(status().isOk());
		for (int i = 1; i <= 9; i++) {
			r.andExpect(jsonPath("$.validationStatus['" + i + "'].status").value("SUCCESS"));
		}
		r.andExpect(jsonPath("$.shLinkContent.label").value("GDHCN Validator"))
				.andExpect(jsonPath("$.shLinkContent.exp").isNumber());
	}

	@Test
	void certificateWithoutExpiryValidates() throws Exception {
		validate(issue(null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.validationStatus['9'].status").value("SUCCESS"));
	}

	@Test
	void expiredShLinkFailsAtExpiryStep() throws Exception {
		validate(issue("2001-01-01T00:00:00.000Z")).andExpect(status().isOk())
				.andExpect(jsonPath("$.validationStatus['8'].status").value("SUCCESS"))
				.andExpect(jsonPath("$.validationStatus['9'].status").value("FAILED"));
	}

	@Test
	void signatureVerifiedAgainstADifferentKeyIsRejected() throws Exception {
		String hc1 = issue(null);
		// the trust list now publishes a different certificate under the same kid
		stubTrustList(Base64.getEncoder().encodeToString(selfSigned(newKeyPair(), "Other").getEncoded()));
		validate(hc1).andExpect(status().isOk())
				.andExpect(jsonPath("$.validationStatus['7'].status").value("FAILED"));
	}
}
