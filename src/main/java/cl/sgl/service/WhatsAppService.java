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
 * Cuando TWILIO_CONTENT_SID / TWILIO_PAYMENT_CONTENT_SID están definidos, usa Content Templates
 * (obligatorio para mensajes iniciados por el negocio sin sesión activa del cliente).
 * Sin Content SID, usa texto libre (requiere que el cliente haya escrito en las últimas 24h).
 *
 * Historia: SGL-034 NOTIF-WA-01, SGL-028 AG-WA-CONF
 */
@Service
@Slf4j
public class WhatsAppService {

    private static final String WHATSAPP_PREFIX = "whatsapp:";
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter VAR_DATE_FMT = DateTimeFormatter.ofPattern("d/M");

    static final String MENU_TEXT =
        "Bienvenido a SGL, Plataforma de Reserva Jurídica.\n\n" +
        "Responde con el número:\n" +
        "1. Consultar mi cita\n" +
        "2. Reagendar mi cita\n" +
        "3. Cancelar mi cita";

    private final String fromNumber;
    private final String contentSid;
    private final String paymentContentSid;
    private final String menuContentSid;
    private final boolean configured;
    private final NotificationLogService notificationLogService;

    @Autowired
    public WhatsAppService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.whatsapp-from:}") String fromNumber,
            @Value("${twilio.content-sid:}") String contentSid,
            @Value("${twilio.payment-content-sid:}") String paymentContentSid,
            @Value("${twilio.menu-content-sid:}") String menuContentSid,
            NotificationLogService notificationLogService) {
        this.fromNumber = fromNumber;
        this.contentSid = contentSid;
        this.paymentContentSid = paymentContentSid;
        this.menuContentSid = menuContentSid;
        this.notificationLogService = notificationLogService;
        this.configured = !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
        if (this.configured) {
            Twilio.init(accountSid, authToken);
            log.info("WhatsAppService inicializado con numero {} | appt-template={} | pago-template={} | menu-template={}",
                fromNumber,
                contentSid.isBlank() ? "ninguno" : contentSid,
                paymentContentSid.isBlank() ? "ninguno" : paymentContentSid,
                menuContentSid.isBlank() ? "ninguno" : menuContentSid);
        } else {
            log.warn("WhatsAppService no configurado — define TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_WHATSAPP_FROM");
        }
    }

    /** Constructor para tests sin ningún template — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, NotificationLogService notificationLogService) {
        this(fromNumber, configured, "", "", "", notificationLogService);
    }

    /** Constructor para tests con template de agendamiento — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, String contentSid, NotificationLogService notificationLogService) {
        this(fromNumber, configured, contentSid, "", "", notificationLogService);
    }

    /** Constructor para tests con templates de agendamiento y pago — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, String contentSid, String paymentContentSid, NotificationLogService notificationLogService) {
        this(fromNumber, configured, contentSid, paymentContentSid, "", notificationLogService);
    }

    /** Constructor para tests con todos los templates — no llama a Twilio.init(). */
    WhatsAppService(String fromNumber, boolean configured, String contentSid, String paymentContentSid, String menuContentSid, NotificationLogService notificationLogService) {
        this.fromNumber = fromNumber;
        this.configured = configured;
        this.contentSid = contentSid;
        this.paymentContentSid = paymentContentSid;
        this.menuContentSid = menuContentSid;
        this.notificationLogService = notificationLogService;
    }

    /**
     * Envía confirmación de pago al cliente por WhatsApp.
     * Si TWILIO_PAYMENT_CONTENT_SID está definido, usa Content Template propio de pago.
     * Si no, usa texto libre. Si WhatsApp falla, registra en notification_log y retorna false.
     *
     * @param appointment agendamiento con pago confirmado
     * @return true si el mensaje fue enviado, false en caso contrario
     */
    public boolean sendPaymentConfirmedWhatsApp(Appointment appointment) {
        if (!configured) {
            log.debug("WhatsApp no configurado — se omite confirmación de pago para {}", appointment.getIdExterno());
            return false;
        }
        String telefono = formatPhone(appointment.getTelefono());
        try {
            if (paymentContentSid != null && !paymentContentSid.isBlank()) {
                doSendWithTemplate(telefono, paymentContentSid, buildPaymentTemplateVariables(appointment));
            } else {
                doSendFreeform(telefono, buildPaymentConfirmedMessage(appointment));
            }
            log.info("WhatsApp de pago confirmado enviado → {} [{}]", telefono, appointment.getIdExterno());
            notificationLogService.logSuccess(appointment.getId(), TipoEmail.CONFIRMACION_CLIENTE,
                NotificationLogService.CANAL_WHATSAPP, telefono);
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar WhatsApp de pago para {} — {}", appointment.getIdExterno(), e.getMessage());
            notificationLogService.logFailure(appointment.getId(), TipoEmail.CONFIRMACION_CLIENTE,
                NotificationLogService.CANAL_WHATSAPP, telefono, e.getMessage());
            return false;
        }
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
     * Envía el menú de bienvenida al número que acaba de escribir.
     * Se invoca desde el webhook de mensajes entrantes de Twilio.
     * Si TWILIO_MENU_CONTENT_SID está definido, usa el Content Template (sin variables).
     * Si no, usa texto libre (válido porque el cliente ya inició la sesión).
     * No registra en notification_log porque no está asociado a ningún agendamiento.
     *
     * @param phone número del cliente en formato E.164 (sin prefijo "whatsapp:")
     * @return true si el mensaje fue enviado, false en caso contrario
     */
    public boolean sendMenuMessage(String phone) {
        if (!configured) {
            log.debug("WhatsApp no configurado — se omite menú para {}", phone);
            return false;
        }
        try {
            if (menuContentSid != null && !menuContentSid.isBlank()) {
                doSendWithTemplate(phone, menuContentSid, "{}");
            } else {
                doSendFreeform(phone, MENU_TEXT);
            }
            log.info("Menú WhatsApp enviado → {}", phone);
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar menú WhatsApp a {} — {}", phone, e.getMessage());
            return false;
        }
    }

    /** Envía usando un Content Template específico por SID. Extraído para tests. */
    void doSendWithTemplate(String toPhone, String sid, String variables) {
        Message.creator(
            new PhoneNumber(WHATSAPP_PREFIX + toPhone),
            new PhoneNumber(WHATSAPP_PREFIX + fromNumber),
            " "
        )
        .setContentSid(sid)
        .setContentVariables(variables)
        .create();
    }

    /** Envía siempre como texto libre — para mensajes sin template configurado. Extraído para tests. */
    void doSendFreeform(String toPhone, String body) {
        Message.creator(
            new PhoneNumber(WHATSAPP_PREFIX + toPhone),
            new PhoneNumber(WHATSAPP_PREFIX + fromNumber),
            body
        ).create();
    }

    /**
     * Envía el mensaje de agendamiento vía API de Twilio.
     * Si hay Content SID de agendamiento, usa el template con variables JSON como payload.
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

    /** Variables JSON para el Content Template de agendamiento (5 variables: nombre, id, servicio, fecha, hora). */
    private String buildTemplateVariables(Appointment appointment) {
        return String.format(
            "{\"1\":\"%s\",\"2\":\"%s\",\"3\":\"%s\",\"4\":\"%s\",\"5\":\"%s\"}",
            appointment.getNombreCliente(),
            appointment.getIdExterno(),
            appointment.getService().getName(),
            appointment.getFecha().format(VAR_DATE_FMT),
            appointment.getHora().format(TIME_FMT));
    }

    /** Variables JSON para el Content Template de confirmación de pago (3 variables: id, fecha, hora). */
    private String buildPaymentTemplateVariables(Appointment appointment) {
        return String.format(
            "{\"1\":\"%s\",\"2\":\"%s\",\"3\":\"%s\"}",
            appointment.getIdExterno(),
            appointment.getFecha().format(VAR_DATE_FMT),
            appointment.getHora().format(TIME_FMT));
    }

    private String buildPaymentConfirmedMessage(Appointment appointment) {
        return String.format(
            "Tu pago fue confirmado. Cita %s agendada para %s a las %s. ¡Te esperamos!",
            appointment.getIdExterno(),
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
            "Revisa tu correo para las instrucciones de pago.",
            appointment.getNombreCliente(),
            appointment.getIdExterno(),
            appointment.getService().getName(),
            appointment.getFecha().format(DATE_FMT),
            appointment.getHora().format(TIME_FMT));
    }
}
