package cl.sgl.service;

/**
 * Estados posibles de una conversación entrante de WhatsApp.
 * El bot guarda el estado por número de teléfono en memoria (TTL: 10 minutos).
 *
 * Historia: SGL-075 WA-CONSULT
 */
public enum WhatsAppConversationState {
    WAITING_FOR_APPOINTMENT_ID
}
