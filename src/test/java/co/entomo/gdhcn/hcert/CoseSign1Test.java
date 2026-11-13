package co.entomo.gdhcn.hcert;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.upokecenter.cbor.CBORObject;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.HeaderKeys;
import COSE.Message;
import COSE.MessageTag;
import COSE.OneKey;
import COSE.Sign1Message;

/**
 * Cross-checks {@link CoseSign1} against the independent cose-java implementation (test scope only)
 * in both directions, and exercises the failure paths a validator must get right.
 */
class CoseSign1Test {

	static final byte[] PAYLOAD = "{\"hello\":\"gdhcn\"}".getBytes(StandardCharsets.UTF_8);
	static KeyPair ec;
	static KeyPair otherEc;
	static KeyPair rsa;

	@BeforeAll
	static void keys() throws Exception {
		ec = ecKey();
		otherEc = ecKey();
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		rsa = kpg.generateKeyPair();
	}

	static KeyPair ecKey() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp256r1"));
		return kpg.generateKeyPair();
	}

	// ---------------------------------------------------------------- happy paths

	@Test
	void signAndVerifyRoundTrip() throws Exception {
		byte[] encoded = CoseSign1.sign(PAYLOAD, "qb4L0atiX/4=", ec.getPrivate());
		CoseSign1 msg = CoseSign1.decode(encoded);
		assertTrue(msg.verify(ec.getPublic()));
		assertArrayEquals(PAYLOAD, msg.payload());
		assertEquals(CoseSign1.ALG_ES256, msg.algorithm());
		assertEquals("qb4L0atiX/4=", msg.kid());
	}

	@Test
	void ourSignatureIsAcceptedByCoseJava() throws Exception {
		byte[] encoded = CoseSign1.sign(PAYLOAD, "kid-1", ec.getPrivate());
		Sign1Message theirs = (Sign1Message) Message.DecodeFromBytes(encoded, MessageTag.Sign1);
		assertTrue(theirs.validate(new OneKey(ec.getPublic(), null)));
		assertArrayEquals(PAYLOAD, theirs.GetContent());
	}

	@Test
	void coseJavaEs256SignatureIsAcceptedByUs() throws Exception {
		byte[] kidBytes = { 1, 2, 3, 4, 5, 6, 7, 8 };
		Sign1Message m = new Sign1Message();
		m.addAttribute(HeaderKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR(), Attribute.PROTECTED);
		m.addAttribute(HeaderKeys.KID, CBORObject.FromObject(kidBytes), Attribute.PROTECTED);
		m.SetContent(PAYLOAD);
		m.sign(new OneKey(null, ec.getPrivate()));

		CoseSign1 msg = CoseSign1.decode(m.EncodeToBytes());
		assertTrue(msg.verify(ec.getPublic()));
		// byte-string kids are exposed Base64 encoded, matching the trust-list format
		assertEquals(Base64.getEncoder().encodeToString(kidBytes), msg.kid());
	}

	/**
	 * cose-java 1.1.0 cannot produce PS256, so this message is assembled independently from RFC 9052
	 * (Sig_structure) and RFC 8812 (RSASSA-PSS, SHA-256, MGF1-SHA-256, 32 byte salt) using plain JCA.
	 */
	@Test
	void ps256SignatureBuiltFromTheRfcsIsAcceptedByUs() throws Exception {
		CBORObject header = CBORObject.NewMap();
		header.Add(CBORObject.FromObject(1), CBORObject.FromObject(-37));
		byte[] protectedBytes = header.EncodeToBytes();

		CBORObject toBeSigned = CBORObject.NewArray();
		toBeSigned.Add(CBORObject.FromObject("Signature1"));
		toBeSigned.Add(CBORObject.FromObject(protectedBytes));
		toBeSigned.Add(CBORObject.FromObject(new byte[0]));
		toBeSigned.Add(CBORObject.FromObject(PAYLOAD));

		java.security.Signature signer = java.security.Signature.getInstance("RSASSA-PSS");
		signer.setParameter(new java.security.spec.PSSParameterSpec("SHA-256", "MGF1",
				java.security.spec.MGF1ParameterSpec.SHA256, 32, 1));
		signer.initSign(rsa.getPrivate());
		signer.update(toBeSigned.EncodeToBytes());

		CBORObject message = CBORObject.NewArray();
		message.Add(CBORObject.FromObject(protectedBytes));
		message.Add(CBORObject.NewMap());
		message.Add(CBORObject.FromObject(PAYLOAD));
		message.Add(CBORObject.FromObject(signer.sign()));

		CoseSign1 msg = CoseSign1.decode(CBORObject.FromObjectAndTag(message, 18).EncodeToBytes());
		assertEquals(CoseSign1.ALG_PS256, msg.algorithm());
		assertTrue(msg.verify(rsa.getPublic()));
		assertFalse(msg.verify(ec.getPublic()), "algorithm/key type mismatch must not verify");
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		assertFalse(msg.verify(kpg.generateKeyPair().getPublic()), "other RSA key must not verify");
	}

	@Test
	void untaggedMessageIsAccepted() throws Exception {
		CBORObject tagged = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate()));
		CoseSign1 msg = CoseSign1.decode(tagged.UntagOne().EncodeToBytes());
		assertTrue(msg.verify(ec.getPublic()));
	}

	@Test
	void missingKidIsNull() throws Exception {
		assertNull(CoseSign1.decode(CoseSign1.sign(PAYLOAD, null, ec.getPrivate())).kid());
	}

	// ---------------------------------------------------------------- rejection paths

	@Test
	void wrongKeyDoesNotVerify() throws Exception {
		CoseSign1 msg = CoseSign1.decode(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate()));
		assertFalse(msg.verify(otherEc.getPublic()));
		assertFalse(msg.verify(null));
	}

	@Test
	void tamperedPayloadDoesNotVerify() throws Exception {
		CBORObject m = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate())).UntagOne();
		byte[] changed = PAYLOAD.clone();
		changed[3] ^= 1;
		m.set(2, CBORObject.FromObject(changed));
		assertFalse(CoseSign1.decode(m.EncodeToBytes()).verify(ec.getPublic()));
	}

	@Test
	void tamperedSignatureDoesNotVerify() throws Exception {
		CBORObject m = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate())).UntagOne();
		byte[] sig = m.get(3).GetByteString();
		sig[10] ^= 1;
		m.set(3, CBORObject.FromObject(sig));
		assertFalse(CoseSign1.decode(m.EncodeToBytes()).verify(ec.getPublic()));
	}

	@Test
	void truncatedSignatureDoesNotVerify() throws Exception {
		CBORObject m = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate())).UntagOne();
		byte[] sig = m.get(3).GetByteString();
		m.set(3, CBORObject.FromObject(java.util.Arrays.copyOf(sig, 63)));
		assertFalse(CoseSign1.decode(m.EncodeToBytes()).verify(ec.getPublic()));
	}

	@Test
	void unsupportedAlgorithmDoesNotVerify() throws Exception {
		// EdDSA (-8) is deliberately not supported
		CBORObject m = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate())).UntagOne();
		CBORObject header = CBORObject.NewMap();
		header.Add(CBORObject.FromObject(1), CBORObject.FromObject(-8));
		m.set(0, CBORObject.FromObject(header.EncodeToBytes()));
		assertFalse(CoseSign1.decode(m.EncodeToBytes()).verify(ec.getPublic()));
	}

	@Test
	void algorithmOnlyInUnprotectedHeaderIsIgnored() throws Exception {
		CBORObject m = CBORObject.DecodeFromBytes(CoseSign1.sign(PAYLOAD, "k", ec.getPrivate())).UntagOne();
		m.set(0, CBORObject.FromObject(new byte[0]));
		CBORObject unprotected = CBORObject.NewMap();
		unprotected.Add(CBORObject.FromObject(1), CBORObject.FromObject(CoseSign1.ALG_ES256));
		m.set(1, unprotected);
		CoseSign1 msg = CoseSign1.decode(m.EncodeToBytes());
		assertNull(msg.algorithm());
		assertFalse(msg.verify(ec.getPublic()));
	}

	@Test
	void signingRequiresAnEcP256Key() throws Exception {
		assertThrows(CoseException.class, () -> CoseSign1.sign(PAYLOAD, "k", rsa.getPrivate()));
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp384r1"));
		KeyPair p384 = kpg.generateKeyPair();
		assertThrows(CoseException.class, () -> CoseSign1.sign(PAYLOAD, "k", p384.getPrivate()));
	}

	// ---------------------------------------------------------------- malformed input

	@Test
	void malformedInputIsRejectedWithCoseException() throws Exception {
		byte[] valid = CoseSign1.sign(PAYLOAD, "k", ec.getPrivate());
		assertThrows(CoseException.class, () -> CoseSign1.decode(null));
		assertThrows(CoseException.class, () -> CoseSign1.decode(new byte[0]));
		assertThrows(CoseException.class, () -> CoseSign1.decode(new byte[] { (byte) 0xff, 0x00 }));
		assertThrows(CoseException.class, () -> CoseSign1.decode(java.util.Arrays.copyOf(valid, valid.length - 5)));
		byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
		assertThrows(CoseException.class, () -> CoseSign1.decode(trailing));
		assertThrows(CoseException.class, () -> CoseSign1.decode(new byte[2 * 1024 * 1024]));
		byte[] random = new byte[256];
		new Random(42).nextBytes(random);
		assertThrows(CoseException.class, () -> CoseSign1.decode(random));
	}

	@Test
	void wrongShapeIsRejected() throws Exception {
		CBORObject threeItems = CBORObject.NewArray();
		threeItems.Add(CBORObject.FromObject(new byte[0]));
		threeItems.Add(CBORObject.NewMap());
		threeItems.Add(CBORObject.FromObject(PAYLOAD));
		assertThrows(CoseException.class, () -> CoseSign1.decode(threeItems.EncodeToBytes()));

		CBORObject detached = CBORObject.NewArray();
		detached.Add(CBORObject.FromObject(new byte[0]));
		detached.Add(CBORObject.NewMap());
		detached.Add(CBORObject.Null);
		detached.Add(CBORObject.FromObject(new byte[64]));
		assertThrows(CoseException.class, () -> CoseSign1.decode(detached.EncodeToBytes()));

		// COSE_Sign (tag 98), not COSE_Sign1
		CBORObject sign = CBORObject.FromObjectAndTag(CBORObject.NewArray(), 98);
		assertThrows(CoseException.class, () -> CoseSign1.decode(sign.EncodeToBytes()));
	}
}
