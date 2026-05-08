package cl.sgl.exception;

/**
 * Excepcion para credenciales invalidas.
 * Historia: SGL-041 ADM-LOGIN
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

