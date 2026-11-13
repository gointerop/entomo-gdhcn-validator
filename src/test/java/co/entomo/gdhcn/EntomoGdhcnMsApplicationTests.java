package co.entomo.gdhcn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.HeaderKeys;
import COSE.Message;
import COSE.MessageTag;
import COSE.OneKey;
import COSE.Sign1Message;
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

	/** Exercises cose-java together with the pinned com.upokecenter:cbor release. */
	@Test
	void coseSign1RoundTrip() throws Exception {
		OneKey key = OneKey.generateKey(AlgorithmID.ECDSA_256);
		byte[] payload = "{\"hello\":\"gdhcn\"}".getBytes(StandardCharsets.UTF_8);

		Sign1Message signed = new Sign1Message();
		signed.addAttribute(HeaderKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR(), Attribute.PROTECTED);
		signed.SetContent(payload);
		signed.sign(key);
		byte[] encoded = signed.EncodeToBytes();

		Message decoded = Message.DecodeFromBytes(encoded, MessageTag.Sign1);
		Sign1Message sign1 = (Sign1Message) decoded;
		assertTrue(sign1.validate(key));
		assertEquals(new String(payload, StandardCharsets.UTF_8), new String(sign1.GetContent(), StandardCharsets.UTF_8));
	}
}
