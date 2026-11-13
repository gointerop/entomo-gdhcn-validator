package co.entomo.gdhcn.hcert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import com.upokecenter.cbor.CBORObject;

import nl.minvws.encoding.Base45;

public class GreenCertificateDecoder {

  /** Upper bound for the inflated COSE message; guards against decompression bombs in untrusted QR codes. */
  public static final int MAX_INFLATED_SIZE = 1 << 20;

  private final PublicKey publicKey;

  public GreenCertificateDecoder(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  /**
   * Decodes base45 encoded string -> Deflate -> COSE -> CBOR -> arbitrary Json String
   *
   * @param base45String the HC1: prefixed certificate string
   * @return the certificate payload as JSON
   * @throws CompressorException if the payload is not valid deflate data
   * @throws IOException on read errors or if the inflated payload is too large
   * @throws CoseException if the COSE structure is malformed
   */
  public String decode(String base45String) throws CompressorException, IOException, CoseException {
    if(!base45String.startsWith("HC1:"))
      throw new RuntimeException("Base45 string not valid according to specification");

    base45String = base45String.substring(4);
    byte[] decodedBytes = Base45.getDecoder().decode(base45String);

    byte[] coseBytes = getCoseBytes(decodedBytes);

    byte[] cborBytes = getCborBytes(coseBytes);

    return getJsonString(cborBytes);
  }

  public static String getJsonString(byte[] cborBytes) throws IOException {
    CBORObject cborObject = CBORObject.DecodeFromBytes(cborBytes);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    cborObject.WriteJSONTo(byteArrayOutputStream);

    return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
  }

  private byte[] getCborBytes(byte[] coseBytes) throws CoseException {
    return CoseSign1.decode(coseBytes).payload();
  }

  public static byte[] getCoseBytes(byte[] decodedBytes) throws CompressorException, IOException {
    try (CompressorInputStream compressedStream = new CompressorStreamFactory()
            .createCompressorInputStream(CompressorStreamFactory.DEFLATE,
                    new ByteArrayInputStream(decodedBytes))) {
      byte[] inflated = compressedStream.readNBytes(MAX_INFLATED_SIZE + 1);
      if (inflated.length > MAX_INFLATED_SIZE) {
        throw new IOException("Inflated payload exceeds " + MAX_INFLATED_SIZE + " bytes");
      }
      return inflated;
    }
  }

  public boolean validate(CoseSign1 msg){
    return msg.verify(publicKey);
  }
}
