package cl.sgl.service;

/**
 * Estados posibles de una conversación entrante de WhatsApp.
 * El bot guarda el estado por número de teléfono en memoria (TTL configurable).
 *
 * Historia: SGL-075 WA-CONSULT, SGL-076 WA-LINK
 */
public enum WhatsAppConversationState {
    /** Opción 1: esperando ID para consultar detalles de la cita. */
    WAITING_FOR_APPOINTMENT_ID,
    /** Opción 2: esperando ID para enviar link de reagendamiento. */
    WAITING_FOR_REAGENDAR_ID,
    /** Opción 3: esperando ID para enviar link de cancelación. */
    WAITING_FOR_CANCELAR_ID
}
