package cl.sgl.entity;

/**
 * Tipo de email almacenado en la cola de reintento.
 * Historia: SGL-038 NOTIF-RETRY, SGL-073 GES-NOTIF
 */
public enum TipoEmail {
    CONFIRMACION_CLIENTE,
    NOTIF_ADMIN,
    REMINDER_24H,
    REMINDER_2H,
    OTP_VERIFICACION,
    CANCELACION_CLIENTE,
    REAGENDAMIENTO_CLIENTE
}
