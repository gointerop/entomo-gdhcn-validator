package co.entomo.gdhcn.hcert;

/**
 * Raised when a COSE structure is malformed or cannot be produced.
 * A signature that simply does not verify is reported as {@code false}, not as an exception.
 */
public class CoseException extends Exception {

    private static final long serialVersionUID = 1L;

    public CoseException(String message) {
        super(message);
    }

    public CoseException(String message, Throwable cause) {
        super(message, cause);
    }
}
