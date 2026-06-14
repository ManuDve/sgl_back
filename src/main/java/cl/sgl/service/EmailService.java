package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.ReminderTipo;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de envío de emails usando el SDK de Mailtrap.
 * La generación de HTML se delega a EmailTemplateBuilder.
 *
 * Si el token no está configurado o el envío falla, se registra el error
 * y el flujo continúa sin interrupción (degraded flow).
 *
 * Historias: SGL-033 NOTIF-EMAIL-01, SGL-036 NOTIF-ADMIN-NEW, SGL-039 NOTIF-TEMPL
 */
@Service
@Slf4j
public class EmailService {

    private static final String FROM_NAME = "Estudio Jurídico SGL";

    private final MailtrapClient       mailtrapClient;
    private final String               fromEmail;
    private final String               adminEmail;
    private final EmailTemplateBuilder templateBuilder;

    @Autowired
    public EmailService(
            @Value("${mailtrap.api.token}")      String apiToken,
            @Value("${mailtrap.api.from-email}") String fromEmail,
            @Value("${admin.email:}")            String adminEmail,
            EmailTemplateBuilder                 templateBuilder) {
        MailtrapConfig config = new MailtrapConfig.Builder()
            .token(apiToken)
            .build();
        this.mailtrapClient  = MailtrapClientFactory.createMailtrapClient(config);
        this.fromEmail       = fromEmail;
        this.adminEmail      = adminEmail;
        this.templateBuilder = templateBuilder;
    }

    /** Constructor para tests unitarios: permite inyectar dependencias mock. */
    EmailService(MailtrapClient mailtrapClient, String fromEmail, String adminEmail,
                 EmailTemplateBuilder templateBuilder) {
        this.mailtrapClient  = mailtrapClient;
        this.fromEmail       = fromEmail;
        this.adminEmail      = adminEmail;
        this.templateBuilder = templateBuilder;
    }

    /**
     * Envía el email de confirmación de pago al cliente.
     * No lanza excepciones: si falla, registra el error y continúa.
     */
    public void sendConfirmationEmail(Appointment appointment) {
        try {
            MailtrapMail mail = MailtrapMail.builder()
                .from(new Address(fromEmail, FROM_NAME))
                .to(List.of(new Address(appointment.getEmail())))
                .subject("Pago confirmado — tu consulta está agendada")
                .html(templateBuilder.buildConfirmationEmail(appointment))
                .build();

            mailtrapClient.send(mail);
            log.info("Email de confirmación enviado → {} [{}]",
                appointment.getEmail(), appointment.getIdExterno());

        } catch (Exception e) {
            log.error("No se pudo enviar email de confirmación para {} — {}",
                appointment.getIdExterno(), e.getMessage());
        }
    }

    /**
     * Envía al administrador una notificación de nuevo agendamiento.
     * Si ADMIN_EMAIL no está configurado, registra una advertencia y continúa.
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
            MailtrapMail mail = MailtrapMail.builder()
                .from(new Address(fromEmail, FROM_NAME))
                .to(List.of(new Address(adminEmail)))
                .subject("Nueva consulta agendada — " + appointment.getIdExterno())
                .html(templateBuilder.buildAdminNotificationEmail(appointment))
                .build();

            mailtrapClient.send(mail);
            log.info("Notificación admin enviada → {} [{}]",
                adminEmail, appointment.getIdExterno());

        } catch (Exception e) {
            log.error("No se pudo enviar notificación admin para {} — {}",
                appointment.getIdExterno(), e.getMessage());
        }
    }

    /**
     * Envía recordatorio de cita al cliente: 24h o 2h antes según el tipo.
     * No lanza excepciones: si falla, registra el error y continúa.
     *
     * Historia: SGL-035 NOTIF-REMIND
     */
    public boolean sendReminderEmail(Appointment appointment, ReminderTipo tipo) {
        try {
            String subject = tipo == ReminderTipo.REMIND_24H
                ? "Recordatorio: tu consulta es mañana — " + appointment.getIdExterno()
                : "Recordatorio: tu consulta es en 2 horas — " + appointment.getIdExterno();

            String html = tipo == ReminderTipo.REMIND_24H
                ? templateBuilder.buildReminderEmail(appointment)
                : templateBuilder.buildReminder2hEmail(appointment);

            MailtrapMail mail = MailtrapMail.builder()
                .from(new Address(fromEmail, FROM_NAME))
                .to(List.of(new Address(appointment.getEmail())))
                .subject(subject)
                .html(html)
                .build();

            mailtrapClient.send(mail);
            log.info("Recordatorio {} enviado → {} [{}]",
                tipo, appointment.getEmail(), appointment.getIdExterno());
            return true;

        } catch (Exception e) {
            log.error("No se pudo enviar recordatorio {} para {} — {}",
                tipo, appointment.getIdExterno(), e.getMessage());
            return false;
        }
    }
}
