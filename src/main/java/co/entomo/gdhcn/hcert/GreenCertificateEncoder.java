package co.entomo.gdhcn.hcert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import com.upokecenter.cbor.CBORObject;

import nl.minvws.encoding.Base45;

public class GreenCertificateEncoder {

    private final PrivateKey privateKey;
    private final String kid;

    public GreenCertificateEncoder(PrivateKey privateKey, String kid) {
        this.privateKey = privateKey;
        this.kid = kid;
    }

    /**
     * Encodes arbitrary Json String to CBOR -> COSE -> Deflate -> BASE45
     *
     * @param json the certificate payload
     * @return the HC1: prefixed certificate string
     * @throws CoseException if signing fails
     * @throws CompressorException if compression fails
     * @throws IOException on write errors
     */
    public String encode(String json) throws CoseException, CompressorException, IOException {

        byte[] cborBytes = getCborBytes(json);

        byte[] coseBytes = CoseSign1.sign(cborBytes, kid, privateKey);

        byte[] deflateBytes = getDeflateBytes(coseBytes);

        return getBase45(deflateBytes);
    }

    private String getBase45(byte[] deflateBytes) {

        return "HC1:" + Base45.getEncoder().encodeToString(deflateBytes);
    }

    private byte[] getDeflateBytes(byte[] messageBytes) throws CompressorException, IOException {
        ByteArrayOutputStream deflateOutputStream = new ByteArrayOutputStream();
        try (CompressorOutputStream deflateOut = new CompressorStreamFactory()
                .createCompressorOutputStream(CompressorStreamFactory.DEFLATE, deflateOutputStream)) {

            deflateOut.write(messageBytes);
        }

        return deflateOutputStream.toByteArray();
    }

    private byte[] getCborBytes(String json) {
        CBORObject cborObject = CBORObject.FromJSONString(json);

        return cborObject.EncodeToBytes();
    }
}
