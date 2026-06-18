package cl.sgl.exception;

/**
 * Se lanza cuando el reagendamiento no puede realizarse por política de negocio
 * (menos de 24h de anticipación, slot ocupado, o cita cancelada).
 * Retorna HTTP 422 Unprocessable Entity.
 *
 * Historia: SGL-064 GES-REAG-WEB
 */
public class RescheduleNotAllowedException extends RuntimeException {

    public RescheduleNotAllowedException(String message) {
        super(message);
    }
}
