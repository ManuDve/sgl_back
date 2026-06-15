package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.EmailRetryQueue;
import cl.sgl.entity.EstadoRetry;
import cl.sgl.entity.ReminderTipo;
import cl.sgl.entity.TipoEmail;
import cl.sgl.repository.EmailRetryQueueRepository;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Servicio de envío de emails usando el SDK de Mailtrap.
 * La generación de HTML se delega a EmailTemplateBuilder.
 *
 * Si el envío falla, el error se registra en email_retry_queue para que
 * RetryScheduler lo reintente con backoff exponencial (hasta 3 veces).
 *
 * Historias: SGL-033 NOTIF-EMAIL-01, SGL-036 NOTIF-ADMIN-NEW,
 *            SGL-039 NOTIF-TEMPL, SGL-038 NOTIF-RETRY
 */
@Service
@Slf4j
public class EmailService {

    private static final String FROM_NAME          = "Estudio Jurídico SGL";
    private static final int    BASE_RETRY_MINUTES = 15;
    private static final int    MAX_ERROR_LENGTH   = 500;
    private static final ZoneId ZONA_CL            = ZoneId.of("America/Santiago");

    private final MailtrapClient            mailtrapClient;
    private final String                    fromEmail;
    private final String                    adminEmail;
    private final EmailTemplateBuilder      templateBuilder;
    private final EmailRetryQueueRepository retryQueueRepository;

    @Autowired
    public EmailService(
            @Value("${mailtrap.api.token}")      String apiToken,
            @Value("${mailtrap.api.from-email}") String fromEmail,
            @Value("${admin.email:}")            String adminEmail,
            EmailTemplateBuilder                 templateBuilder,
            EmailRetryQueueRepository            retryQueueRepository) {
        MailtrapConfig config = new MailtrapConfig.Builder()
            .token(apiToken)
            .build();
        this.mailtrapClient      = MailtrapClientFactory.createMailtrapClient(config);
        this.fromEmail           = fromEmail;
        this.adminEmail          = adminEmail;
        this.templateBuilder     = templateBuilder;
        this.retryQueueRepository = retryQueueRepository;
    }

    /** Constructor para tests unitarios: permite inyectar dependencias mock. */
    EmailService(MailtrapClient mailtrapClient, String fromEmail, String adminEmail,
                 EmailTemplateBuilder templateBuilder, EmailRetryQueueRepository retryQueueRepository) {
        this.mailtrapClient      = mailtrapClient;
        this.fromEmail           = fromEmail;
        this.adminEmail          = adminEmail;
        this.templateBuilder     = templateBuilder;
        this.retryQueueRepository = retryQueueRepository;
    }

    /**
     * Envía el email de confirmación de pago al cliente.
     * Si falla, encola el reintento en email_retry_queue.
     */
    public void sendConfirmationEmail(Appointment appointment) {
        try {
            mailtrapClient.send(buildConfirmationMail(appointment));
            log.info("Email de confirmación enviado → {} [{}]",
                appointment.getEmail(), appointment.getIdExterno());
        } catch (Exception e) {
            log.error("No se pudo enviar email de confirmación para {} — {}",
                appointment.getIdExterno(), e.getMessage());
            enqueueRetry(appointment.getId(), TipoEmail.CONFIRMACION_CLIENTE, e.getMessage());
        }
    }

    /**
     * Envía al administrador una notificación de nuevo agendamiento.
     * Si ADMIN_EMAIL no está configurado, registra una advertencia y continúa.
     * Si falla el envío, encola el reintento.
     *
     * Historia: SGL-036 NOTIF-ADMIN-NEW
     */
    public void sendAdminNewAppointmentEmail(Appointment appointment) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("ADMIN_EMAIL no configurado — se omite notificación admin [{}]",
                appointment.getIdExterno());
            return;
        }
        try {
            mailtrapClient.send(buildAdminMail(appointment));
            log.info("Notificación admin enviada → {} [{}]",
                adminEmail, appointment.getIdExterno());
        } catch (Exception e) {
            log.error("No se pudo enviar notificación admin para {} — {}",
                appointment.getIdExterno(), e.getMessage());
            enqueueRetry(appointment.getId(), TipoEmail.NOTIF_ADMIN, e.getMessage());
        }
    }

    /**
     * Envía recordatorio de cita al cliente: 24h o 2h antes según el tipo.
     * Si falla, encola el reintento y retorna false.
     *
     * Historia: SGL-035 NOTIF-REMIND
     */
    public boolean sendReminderEmail(Appointment appointment, ReminderTipo tipo) {
        try {
            mailtrapClient.send(buildReminderMail(appointment, tipo));
            log.info("Recordatorio {} enviado → {} [{}]",
                tipo, appointment.getEmail(), appointment.getIdExterno());
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar recordatorio {} para {} — {}",
                tipo, appointment.getIdExterno(), e.getMessage());
            TipoEmail tipoEmail = (tipo == ReminderTipo.REMIND_24H)
                ? TipoEmail.REMINDER_24H : TipoEmail.REMINDER_2H;
            enqueueRetry(appointment.getId(), tipoEmail, e.getMessage());
            return false;
        }
    }

    /**
     * Reintenta enviar un email de la cola de reintentos.
     * No captura excepciones ni encola nuevamente — RetryScheduler gestiona el resultado.
     *
     * Historia: SGL-038 NOTIF-RETRY
     */
    void retryEmail(EmailRetryQueue entry, Appointment appointment) throws Exception {
        MailtrapMail mail = switch (entry.getTipoEmail()) {
            case CONFIRMACION_CLIENTE -> buildConfirmationMail(appointment);
            case NOTIF_ADMIN          -> buildAdminMail(appointment);
            case REMINDER_24H         -> buildReminderMail(appointment, ReminderTipo.REMIND_24H);
            case REMINDER_2H          -> buildReminderMail(appointment, ReminderTipo.REMIND_2H);
        };
        mailtrapClient.send(mail);
        log.info("Reintento exitoso — tipo={} appt={}",
            entry.getTipoEmail(), appointment.getIdExterno());
    }

    // ── Mail builders ──────────────────────────────────────────────────────

    private MailtrapMail buildConfirmationMail(Appointment appointment) {
        return MailtrapMail.builder()
            .from(new Address(fromEmail, FROM_NAME))
            .to(List.of(new Address(appointment.getEmail())))
            .subject("Pago confirmado — tu consulta está agendada")
            .html(templateBuilder.buildConfirmationEmail(appointment))
            .build();
    }

    private MailtrapMail buildAdminMail(Appointment appointment) {
        return MailtrapMail.builder()
            .from(new Address(fromEmail, FROM_NAME))
            .to(List.of(new Address(adminEmail)))
            .subject("Nueva consulta agendada — " + appointment.getIdExterno())
            .html(templateBuilder.buildAdminNotificationEmail(appointment))
            .build();
    }

    private MailtrapMail buildReminderMail(Appointment appointment, ReminderTipo tipo) {
        String subject = (tipo == ReminderTipo.REMIND_24H)
            ? "Recordatorio: tu consulta es mañana — " + appointment.getIdExterno()
            : "Recordatorio: tu consulta es en 2 horas — " + appointment.getIdExterno();
        String html = (tipo == ReminderTipo.REMIND_24H)
            ? templateBuilder.buildReminderEmail(appointment)
            : templateBuilder.buildReminder2hEmail(appointment);
        return MailtrapMail.builder()
            .from(new Address(fromEmail, FROM_NAME))
            .to(List.of(new Address(appointment.getEmail())))
            .subject(subject)
            .html(html)
            .build();
    }

    // ── Cola de reintento ──────────────────────────────────────────────────

    private void enqueueRetry(Long appointmentId, TipoEmail tipoEmail, String errorMessage) {
        try {
            String errorTruncado = (errorMessage != null)
                ? errorMessage.substring(0, Math.min(errorMessage.length(), MAX_ERROR_LENGTH))
                : null;
            retryQueueRepository.save(EmailRetryQueue.builder()
                .appointmentId(appointmentId)
                .tipoEmail(tipoEmail)
                .intentos(0)
                .proximoIntento(LocalDateTime.now(ZONA_CL).plusMinutes(BASE_RETRY_MINUTES))
                .estado(EstadoRetry.PENDIENTE)
                .ultimoError(errorTruncado)
                .build());
            log.info("Email encolado para reintento — tipo={} appt={}", tipoEmail, appointmentId);
        } catch (Exception ex) {
            log.error("No se pudo encolar reintento — tipo={} appt={} — {}",
                tipoEmail, appointmentId, ex.getMessage());
        }
    }
}
