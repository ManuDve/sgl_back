package cl.sgl.exception;

/**
 * Se lanza cuando la cancelación no puede realizarse por política de negocio
 * (menos de 24h de anticipación o cita ya cancelada).
 * Retorna HTTP 422 Unprocessable Entity.
 *
 * Historia: SGL-065 GES-CANCEL-WEB
 */
public class CancellationNotAllowedException extends RuntimeException {

    public CancellationNotAllowedException(String message) {
        super(message);
    }
}
