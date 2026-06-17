package cl.sgl.entity;

/**
 * Tipo de email almacenado en la cola de reintento.
 * Historia: SGL-038 NOTIF-RETRY
 */
public enum TipoEmail {
    CONFIRMACION_CLIENTE,
    NOTIF_ADMIN,
    REMINDER_24H,
    REMINDER_2H,
    OTP_VERIFICACION
}
