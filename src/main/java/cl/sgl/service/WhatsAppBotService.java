package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.repository.AppointmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot de WhatsApp con estado por conversación (en memoria, TTL configurable).
 *
 * Flujo actual:
 *   [sin sesión] → cualquier mensaje → menú de opciones
 *   [sin sesión] → "1"              → WAITING_FOR_APPOINTMENT_ID → solicita ID
 *   [WAITING_FOR_APPOINTMENT_ID]    → recibe ID → consulta y responde con detalle o error
 *
 * Historia: SGL-075 WA-CONSULT
 */
@Service
@Slf4j
public class WhatsAppBotService {

    private final int sessionTtlMinutes;

    static final String MSG_ASK_ID =
        "Por favor, ingresa tu ID de cita (ej: *AG-2026-0001*):";

    static final String MSG_NOT_FOUND_TEMPLATE =
        "No encontramos ninguna cita con el ID \"%s\".\n\n" +
        "Verifica el ID e intenta nuevamente escribiendo *1*.";

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppService whatsAppService;
    private final AppointmentRepository appointmentRepository;

    // phone → ConversationEntry (in-memory, no persiste entre reinicios)
    private final ConcurrentHashMap<String, ConversationEntry> sessions = new ConcurrentHashMap<>();

    public WhatsAppBotService(
            WhatsAppService whatsAppService,
            AppointmentRepository appointmentRepository,
            @Value("${whatsapp.bot.session-ttl-minutes:10}") int sessionTtlMinutes) {
        this.whatsAppService = whatsAppService;
        this.appointmentRepository = appointmentRepository;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    /**
     * Punto de entrada del bot: enruta el mensaje según el estado de la sesión activa.
     *
     * @param phone número en formato E.164 (sin prefijo "whatsapp:")
     * @param body  texto recibido (puede ser null si el usuario envió media)
     */
    public void handleMessage(String phone, String body) {
        String text = body != null ? body.trim() : "";

        ConversationEntry current = sessions.get(phone);

        if (current == null || isExpired(current)) {
            sessions.remove(phone);
            handleMenuState(phone, text);
        } else {
            handleActiveSession(phone, text, current.state());
        }
    }

    // ── Handlers por estado ──────────────────────────────────────────────────

    private void handleMenuState(String phone, String text) {
        if ("1".equals(text)) {
            sessions.put(phone, new ConversationEntry(
                WhatsAppConversationState.WAITING_FOR_APPOINTMENT_ID,
                LocalDateTime.now()));
            whatsAppService.sendBotReply(phone, MSG_ASK_ID);
        } else {
            whatsAppService.sendMenuMessage(phone);
        }
    }

    private void handleActiveSession(String phone, String text, WhatsAppConversationState state) {
        sessions.remove(phone);
        if (state == WhatsAppConversationState.WAITING_FOR_APPOINTMENT_ID) {
            lookupAppointment(phone, text);
        }
    }

    private void lookupAppointment(String phone, String idExterno) {
        Optional<Appointment> opt = appointmentRepository.findByIdExterno(idExterno.toUpperCase());
        if (opt.isPresent()) {
            log.info("Consulta WhatsApp de cita {} desde {}", idExterno.toUpperCase(), phone);
            whatsAppService.sendBotReply(phone, buildDetailsMessage(opt.get()));
        } else {
            log.warn("Consulta WhatsApp: cita no encontrada — {} desde {}", idExterno, phone);
            whatsAppService.sendBotReply(phone, String.format(MSG_NOT_FOUND_TEMPLATE, idExterno));
        }
    }

    // ── Builders de mensajes ─────────────────────────────────────────────────

    String buildDetailsMessage(Appointment a) {
        return "Información de tu cita *" + a.getIdExterno() + "*:\n\n" +
            "Servicio: " + a.getService().getName() + "\n" +
            "Fecha: " + a.getFecha().format(DATE_FMT) + "\n" +
            "Hora: " + a.getHora().format(TIME_FMT) + "\n" +
            "Estado: " + formatEstado(a) + "\n\n" +
            "Para volver al menú, escribe cualquier mensaje.";
    }

    private String formatEstado(Appointment a) {
        if (Boolean.TRUE.equals(a.getReagendado())) return "Reagendada";
        return switch (a.getEstado()) {
            case PENDING   -> "Pendiente de pago";
            case CONFIRMED -> "Confirmada";
            case CANCELLED -> "Cancelada";
            default        -> a.getEstado().name();
        };
    }

    // ── Gestión de sesiones ──────────────────────────────────────────────────

    private boolean isExpired(ConversationEntry entry) {
        return entry.createdAt()
            .plusMinutes(sessionTtlMinutes)
            .isBefore(LocalDateTime.now());
    }

    /** Elimina la sesión activa de un número (útil para tests). */
    void clearSession(String phone) {
        sessions.remove(phone);
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    record ConversationEntry(WhatsAppConversationState state, LocalDateTime createdAt) {}
}
