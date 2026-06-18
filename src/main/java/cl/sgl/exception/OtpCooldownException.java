package cl.sgl.exception;

/**
 * Se lanza cuando el cliente intenta solicitar un nuevo OTP
 * antes de que expire el período de espera de 60 segundos.
 *
 * Historia: SGL-066 GES-OTP (cooldown)
 */
public class OtpCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public OtpCooldownException(long retryAfterSeconds) {
        super("Debes esperar " + retryAfterSeconds + " segundo" +
              (retryAfterSeconds == 1 ? "" : "s") +
              " antes de solicitar un nuevo código.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
