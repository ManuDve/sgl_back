package cl.sgl.entity;

/**
 * Estado del intento de reenvío en la cola de emails.
 * Historia: SGL-038 NOTIF-RETRY
 */
public enum EstadoRetry {
    PENDIENTE,
    ENVIADO,
    FALLIDO
}
