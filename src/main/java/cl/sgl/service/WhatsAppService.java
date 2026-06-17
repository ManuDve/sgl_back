package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.TipoEmail;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Envía notificaciones de WhatsApp vía Twilio.
 * Si las variables TWILIO_* no están configuradas, los métodos retornan false sin lanzar excepción.
 * Cuando TWILIO_CONTENT_SID está definido, usa Content Templates (obligatorio para mensajes
 * iniciados por el negocio sin sesión activa del cliente). Sin Content SID, usa texto libre
 * (solo funciona si el cliente escribió en las últimas 24h).
 *
 * Historia: SGL-034 NOTIF-WA-01
 */
@Service
@Slf4j
public class WhatsAppService {

    private static final String WHATSAPP_PREFIX = "whatsapp:";
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter VAR_DATE_FMT = DateTimeFormatter.ofPattern("d/M");

    private final String fromNumber;
    private final String contentSid;
    private final boolean configured;
    private final NotificationLogService notificationLogService;

    @Autowired
    public WhatsAppService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.whatsapp-from:}") String fromNumber,
            @Value("${twilio.content-sid:}") String contentSid,
            NotificationLogService notificationLogService) {
        this.fromNumber = fromNumber;
        this.contentSid = contentSid;
        this.notificationLogService = notificationLogService;
        this.configured = !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
        if (this.configured) {
            Twilio.init(accountSid, authToken);
            log.info("WhatsAppService inicializado con numero {} | template={}",
                fromNumber, contentSid.isBlank() ? "ninguno (texto libre)" : contentSid);
        } else {
            log.warn("WhatsAppService no configurado — define TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_WHATSAPP_FROM");
        }
    }

    /** Constructor para tests sin template — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, NotificationLogService notificationLogService) {
        this(fromNumber, configured, "", notificationLogService);
    }

    /** Constructor para tests con template — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, String contentSid, NotificationLogService notificationLogService) {
        this.fromNumber = fromNumber;
        this.configured = configured;
        this.contentSid = contentSid;
        this.notificationLogService = notificationLogService;
    }

    /**
     * Envía confirmación de solicitud de agendamiento al cliente por WhatsApp.
     * Si WhatsApp falla, registra el error en notification_log y retorna false — no lanza excepción.
     *
     * @param appointment agendamiento recién creado
     * @return true si el mensaje fue enviado, false en caso contrario
     */
    public boolean sendConfirmationWhatsApp(Appointment appointment) {
        if (!configured) {
            log.debug("WhatsApp no configurado — se omite notificación para {}", appointment.getIdExterno());
            return false;
        }
        String telefono = formatPhone(appointment.getTelefono());
        try {
            String payload = useTemplate()
                ? buildTemplateVariables(appointment)
                : buildFreeformMessage(appointment);
            doSend(telefono, payload);
            log.info("WhatsApp enviado → {} [{}]", telefono, appointment.getIdExterno());
            notificationLogService.logSuccess(appointment.getId(), TipoEmail.CONFIRMACION_CLIENTE,
                NotificationLogService.CANAL_WHATSAPP, telefono);
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar WhatsApp para {} — {}", appointment.getIdExterno(), e.getMessage());
            notificationLogService.logFailure(appointment.getId(), TipoEmail.CONFIRMACION_CLIENTE,
                NotificationLogService.CANAL_WHATSAPP, telefono, e.getMessage());
            return false;
        }
    }

    /**
     * Envía el mensaje vía API de Twilio.
     * Si hay Content SID, usa el template con variables JSON como payload.
     * Si no, usa texto libre como body (requiere sesión activa del cliente).
     * Extraído en método propio para facilitar tests.
     */
    void doSend(String toPhone, String payload) {
        var creator = Message.creator(
            new PhoneNumber(WHATSAPP_PREFIX + toPhone),
            new PhoneNumber(WHATSAPP_PREFIX + fromNumber),
            useTemplate() ? " " : payload
        );
        if (useTemplate()) {
            creator.setContentSid(contentSid);
            creator.setContentVariables(payload);
        }
        creator.create();
    }

    /** Normaliza el número a formato E.164 (+569XXXXXXXX para Chile). */
    String formatPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("56") && digits.length() == 11) {
            return "+" + digits;
        }
        if (digits.length() == 9) {
            return "+56" + digits;
        }
        return "+" + digits;
    }

    private boolean useTemplate() {
        return contentSid != null && !contentSid.isBlank();
    }

    /** Variables JSON para el Content Template (5 variables: nombre, id, servicio, fecha, hora). */
    private String buildTemplateVariables(Appointment appointment) {
        return String.format(
            "{\"1\":\"%s\",\"2\":\"%s\",\"3\":\"%s\",\"4\":\"%s\",\"5\":\"%s\"}",
            appointment.getNombreCliente(),
            appointment.getIdExterno(),
            appointment.getService().getName(),
            appointment.getFecha().format(VAR_DATE_FMT),
            appointment.getHora().format(TIME_FMT));
    }

    /** Texto libre para sesiones activas (el cliente escribió en las últimas 24h). */
    private String buildFreeformMessage(Appointment appointment) {
        return String.format(
            "Hola, %s. Recibimos tu solicitud de consulta legal.\n\n" +
            "ID: %s\n" +
            "Servicio: %s\n" +
            "Fecha: %s\n" +
            "Hora: %s\n\n" +
            "Coordinaremos el pago contigo a la brevedad.",
            appointment.getNombreCliente(),
            appointment.getIdExterno(),
            appointment.getService().getName(),
            appointment.getFecha().format(DATE_FMT),
            appointment.getHora().format(TIME_FMT));
    }
}
