package co.entomo.gdhcn.hcert;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

/**
 * Minimal, strict COSE_Sign1 (RFC 9052 section 4.2) for health certificates (HCERT).
 *
 * <ul>
 *   <li>Signing: ES256 only (ECDSA P-256 / SHA-256), as used for GDHCN DSC keys.</li>
 *   <li>Verification: ES256 and PS256 (RSASSA-PSS / SHA-256, modulus of at least 2048 bits).</li>
 *   <li>The algorithm is read from the <em>protected</em> header only and must match the type of
 *       the supplied key; anything else (including EdDSA) fails verification.</li>
 * </ul>
 */
public final class CoseSign1 {

    public static final int ALG_ES256 = -7;
    public static final int ALG_PS256 = -37;

    private static final int TAG_SIGN1 = 18;
    private static final int LABEL_ALG = 1;
    private static final int LABEL_KID = 4;
    private static final int MAX_ENCODED_SIZE = 1 << 20;
    private static final int ES256_SIGNATURE_LENGTH = 64;

    private final byte[] protectedBytes;
    private final CBORObject protectedHeader;
    private final CBORObject unprotectedHeader;
    private final byte[] payload;
    private final byte[] signature;

    private CoseSign1(byte[] protectedBytes, CBORObject protectedHeader, CBORObject unprotectedHeader, byte[] payload,
            byte[] signature) {
        this.protectedBytes = protectedBytes;
        this.protectedHeader = protectedHeader;
        this.unprotectedHeader = unprotectedHeader;
        this.payload = payload;
        this.signature = signature;
    }

    // ------------------------------------------------------------------ signing

    /**
     * Signs {@code payload} with ES256. The key id is written to the protected header as a text string,
     * which is what the GDHCN trust list publishes.
     */
    public static byte[] sign(byte[] payload, String kid, PrivateKey key) throws CoseException {
        if (!(key instanceof ECKey ec) || fieldSize(ec) != 256) {
            throw new CoseException("ES256 signing requires an EC P-256 private key");
        }
        CBORObject header = CBORObject.NewMap();
        header.Add(CBORObject.FromObject(LABEL_ALG), CBORObject.FromObject(ALG_ES256));
        if (kid != null) {
            header.Add(CBORObject.FromObject(LABEL_KID), CBORObject.FromObject(kid));
        }
        byte[] protectedBytes = header.EncodeToBytes();
        byte[] signature;
        try {
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(key);
            signer.update(sigStructure(protectedBytes, payload));
            signature = signer.sign();
        } catch (GeneralSecurityException e) {
            throw new CoseException("Unable to sign", e);
        }
        CBORObject message = CBORObject.NewArray();
        message.Add(CBORObject.FromObject(protectedBytes));
        message.Add(CBORObject.NewMap());
        message.Add(CBORObject.FromObject(payload));
        message.Add(CBORObject.FromObject(signature));
        return CBORObject.FromObjectAndTag(message, TAG_SIGN1).EncodeToBytes();
    }

    // ------------------------------------------------------------------ decoding

    /** Parses a COSE_Sign1, tagged (18) or untagged. Does not verify the signature. */
    public static CoseSign1 decode(byte[] encoded) throws CoseException {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_ENCODED_SIZE) {
            throw new CoseException("COSE message is empty or too large");
        }
        try {
            CBORObject message = CBORObject.DecodeFromBytes(encoded);
            if (message.isTagged()) {
                if (!message.HasMostOuterTag(TAG_SIGN1) || message.GetAllTags().length != 1) {
                    throw new CoseException("Not a COSE_Sign1 message (unexpected CBOR tag)");
                }
                message = message.UntagOne();
            }
            if (message.getType() != CBORType.Array || message.size() != 4) {
                throw new CoseException("COSE_Sign1 must be an array of four elements");
            }
            CBORObject protectedElement = message.get(0);
            CBORObject unprotected = message.get(1);
            CBORObject payloadElement = message.get(2);
            CBORObject signatureElement = message.get(3);
            if (protectedElement.getType() != CBORType.ByteString || unprotected.getType() != CBORType.Map
                    || signatureElement.getType() != CBORType.ByteString) {
                throw new CoseException("COSE_Sign1 elements have unexpected types");
            }
            if (payloadElement.getType() != CBORType.ByteString) {
                throw new CoseException("COSE_Sign1 payload must be a byte string (detached payloads are unsupported)");
            }
            byte[] protectedBytes = protectedElement.GetByteString();
            CBORObject protectedMap = protectedBytes.length == 0 ? CBORObject.NewMap()
                    : CBORObject.DecodeFromBytes(protectedBytes);
            if (protectedMap.getType() != CBORType.Map) {
                throw new CoseException("Protected header must be a map");
            }
            return new CoseSign1(protectedBytes, protectedMap, unprotected, payloadElement.GetByteString(),
                    signatureElement.GetByteString());
        } catch (CoseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CoseException("Malformed COSE message: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ accessors

    public byte[] payload() {
        return payload.clone();
    }

    /** The COSE algorithm identifier from the protected header, or {@code null} if absent. */
    public Integer algorithm() {
        CBORObject alg = protectedHeader.get(CBORObject.FromObject(LABEL_ALG));
        if (alg == null || !alg.isNumber() || !alg.CanValueFitInInt32()) {
            return null;
        }
        return alg.AsInt32Value();
    }

    /**
     * The key identifier. A text string is returned as is; a byte string (the form used by EU DCC
     * and WHO DSCs) is returned standard-Base64 encoded, which is how the trust list publishes kids.
     * The protected header takes precedence over the unprotected one.
     */
    public String kid() {
        CBORObject kid = protectedHeader.get(CBORObject.FromObject(LABEL_KID));
        if (kid == null) {
            kid = unprotectedHeader.get(CBORObject.FromObject(LABEL_KID));
        }
        if (kid == null) {
            return null;
        }
        if (kid.getType() == CBORType.TextString) {
            return kid.AsString();
        }
        if (kid.getType() == CBORType.ByteString) {
            return Base64.getEncoder().encodeToString(kid.GetByteString());
        }
        return null;
    }

    public String protectedHeaderToString() {
        return protectedHeader.toString();
    }

    // ------------------------------------------------------------------ verification

    /**
     * Verifies the signature with {@code key}. Returns {@code false} for a bad signature, an
     * unsupported or missing algorithm, or a key that does not suit the algorithm.
     */
    public boolean verify(PublicKey key) {
        Integer alg = algorithm();
        if (alg == null || key == null) {
            return false;
        }
        try {
            byte[] toBeSigned = sigStructure(protectedBytes, payload);
            if (alg == ALG_ES256) {
                if (!(key instanceof ECKey ec) || fieldSize(ec) != 256 || signature.length != ES256_SIGNATURE_LENGTH) {
                    return false;
                }
                Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
                verifier.initVerify(key);
                verifier.update(toBeSigned);
                return verifier.verify(signature);
            }
            if (alg == ALG_PS256) {
                if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048) {
                    return false;
                }
                Signature verifier = Signature.getInstance("RSASSA-PSS");
                verifier.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
                verifier.initVerify(key);
                verifier.update(toBeSigned);
                return verifier.verify(signature);
            }
            return false;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ internals

    private static int fieldSize(ECKey key) {
        return key.getParams().getCurve().getField().getFieldSize();
    }

    /** Sig_structure = ["Signature1", protected, external_aad, payload] (RFC 9052 section 4.4). */
    private static byte[] sigStructure(byte[] protectedBytes, byte[] payload) {
        CBORObject structure = CBORObject.NewArray();
        structure.Add(CBORObject.FromObject("Signature1"));
        structure.Add(CBORObject.FromObject(protectedBytes));
        structure.Add(CBORObject.FromObject(new byte[0]));
        structure.Add(CBORObject.FromObject(payload));
        return structure.EncodeToBytes();
    }
}
