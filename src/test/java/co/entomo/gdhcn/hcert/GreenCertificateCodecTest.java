package co.entomo.gdhcn.hcert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.Test;

class GreenCertificateCodecTest {

	@Test
	void encodeDecodeAndVerify() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair kp = kpg.generateKeyPair();

		String hc1 = new GreenCertificateEncoder(kp.getPrivate(), "kid").encode("{\"1\":\"XM\",\"4\":4102444800}");
		assertTrue(hc1.startsWith("HC1:"));

		GreenCertificateDecoder decoder = new GreenCertificateDecoder(kp.getPublic());
		assertEquals("{\"1\":\"XM\",\"4\":4102444800}", decoder.decode(hc1));

		byte[] cose = GreenCertificateDecoder.getCoseBytes(
				nl.minvws.encoding.Base45.getDecoder().decode(hc1.substring(4)));
		assertTrue(decoder.validate(CoseSign1.decode(cose)));
	}

	@Test
	void inflationBombIsRejected() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (CompressorOutputStream deflate = new CompressorStreamFactory()
				.createCompressorOutputStream(CompressorStreamFactory.DEFLATE, out)) {
			deflate.write(new byte[8 * 1024 * 1024]); // compresses to a few KiB
		}
		byte[] bomb = out.toByteArray();
		assertTrue(bomb.length < 64 * 1024, "test payload should be tiny when compressed");
		assertThrows(java.io.IOException.class, () -> GreenCertificateDecoder.getCoseBytes(bomb));
	}
}
