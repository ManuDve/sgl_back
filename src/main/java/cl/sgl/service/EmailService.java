package cl.sgl.service;

import cl.sgl.entity.Appointment;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Servicio de envío de emails usando el SDK de Mailtrap.
 * El token se inyecta desde la variable de entorno MAILTRAP_API_TOKEN.
 *
 * Si el token no está configurado o el envío falla, se registra el error
 * y el flujo de agendamiento continúa sin interrupción (degraded flow).
 *
 * Historias: SGL-033 NOTIF-EMAIL-01, SGL-037 NOTIF-ID, SGL-094 RISK-WA-FALLBACK
 */
@Service
@Slf4j
public class EmailService {

    private static final String FROM_NAME = "Estudio Jurídico SGL";

    private final MailtrapClient mailtrapClient;
    private final String         fromEmail;

    @Autowired
    public EmailService(
            @Value("${mailtrap.api.token}")    String apiToken,
            @Value("${mailtrap.api.from-email}") String fromEmail) {
        MailtrapConfig config = new MailtrapConfig.Builder()
            .token(apiToken)
            .build();
        this.mailtrapClient = MailtrapClientFactory.createMailtrapClient(config);
        this.fromEmail      = fromEmail;
    }

    /** Constructor para tests unitarios: permite inyectar un cliente mock. */
    EmailService(MailtrapClient mailtrapClient, String fromEmail) {
        this.mailtrapClient = mailtrapClient;
        this.fromEmail      = fromEmail;
    }

    /**
     * Envía el email de confirmación de agendamiento al cliente.
     * No lanza excepciones: si falla, registra el error y continúa.
     *
     * @param appointment agendamiento recién creado
     */
    public void sendConfirmationEmail(Appointment appointment) {
        try {
            MailtrapMail mail = MailtrapMail.builder()
                .from(new Address(fromEmail, FROM_NAME))
                .to(List.of(new Address(appointment.getEmail())))
                .subject("Pago confirmado — tu consulta está agendada")
                .html(buildHtml(appointment))
                .build();

            mailtrapClient.send(mail);
            log.info("Email de confirmación enviado → {} [{}]",
                appointment.getEmail(), appointment.getIdExterno());

        } catch (Exception e) {
            log.error("No se pudo enviar email de confirmación para {} — {}",
                appointment.getIdExterno(), e.getMessage());
        }
    }

    // ── Plantilla HTML ────────────────────────────────────────────────
    // Usa {placeholder} en lugar de %s para evitar conflictos con % del CSS.

    private static final String HTML_TEMPLATE =
        "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"/>" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/><style>" +
        "body{margin:0;padding:0;background:#f0f0f0;font-family:Arial,sans-serif;}" +
        ".wrap{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);}" +
        ".hdr{background:#0A0A0A;padding:24px 32px;}" +
        ".logo{color:#C9A84C;font-size:22px;font-weight:bold;margin:0;letter-spacing:2px;}" +
        ".sub{color:#6B6B6B;font-size:12px;margin:4px 0 0;}" +
        ".bdy{padding:32px;}" +
        ".box{background:#f9f9f9;border-left:4px solid #C9A84C;border-radius:4px;padding:20px 24px;margin:0 0 24px;}" +
        ".box table{width:100%;border-collapse:collapse;}" +
        ".box tr td{padding:8px 0;border-bottom:1px solid #eaeaea;font-size:14px;color:#333;}" +
        ".box tr:last-child td{border-bottom:none;}" +
        ".lbl{color:#888;width:40%;}" +
        ".val{font-weight:bold;text-align:right;}" +
        ".id{background:#0A0A0A;color:#C9A84C;font-weight:bold;font-size:14px;display:inline-block;padding:3px 10px;border-radius:4px;}" +
        ".note{font-size:13px;color:#555;line-height:1.6;margin:0 0 14px;}" +
        ".ftr{background:#1A1A1A;padding:18px 32px;text-align:center;}" +
        ".ftr p{color:#6B6B6B;font-size:11px;margin:3px 0;}" +
        "</style></head><body><div class=\"wrap\">" +
        "<div class=\"hdr\"><p class=\"logo\">SGL</p><p class=\"sub\">Estudio Jurídico · Santiago, Chile</p></div>" +
        "<div class=\"bdy\">" +
        "<p style=\"font-size:16px;color:#222;margin:0 0 14px;\">Estimado/a <strong>{nombre}</strong>,</p>" +
        "<p class=\"note\">Tu pago fue aprobado y tu consulta ha quedado <strong style=\"color:#C9A84C;\">confirmada</strong>. Aquí están los detalles:</p>" +

        "<div class=\"box\"><table>" +
        "<tr><td class=\"lbl\">N° de cita</td><td class=\"val\"><span class=\"id\">{idExterno}</span></td></tr>" +
        "<tr><td class=\"lbl\">Servicio</td><td class=\"val\">{servicio}</td></tr>" +
        "<tr><td class=\"lbl\">Fecha</td><td class=\"val\">{fecha}</td></tr>" +
        "<tr><td class=\"lbl\">Hora</td><td class=\"val\">{hora}</td></tr>" +
        "<tr><td class=\"lbl\">Monto pagado</td><td class=\"val\" style=\"color:#C9A84C;\">{monto}</td></tr>" +
        "<tr><td class=\"lbl\">Código de transacción</td><td class=\"val\" style=\"font-family:monospace;\">{codigoTransaccion}</td></tr>" +
        "</table></div>" +

        "<p class=\"note\">Si necesitas cancelar o reagendar tu cita, contáctanos con al menos 24 horas de anticipación.</p>" +
        "<p class=\"note\">¿Tienes dudas? Escríbenos a <a href=\"mailto:contacto@sglabogados.cl\" " +
        "style=\"color:#C9A84C;\">contacto@sglabogados.cl</a></p>" +
        "</div>" +
        "<div class=\"ftr\"><p>&copy; {anio} SGL Estudio Jurídico &middot; Santiago, Chile</p>" +
        "<p>Este mensaje fue generado automáticamente, no respondas a este correo.</p></div>" +
        "</div></body></html>";

    private String buildHtml(Appointment appointment) {
        String fecha = appointment.getFecha()
            .format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CL")));
        String hora  = appointment.getHora()
            .format(DateTimeFormatter.ofPattern("HH:mm"));

        String codigo = appointment.getCodigoTransaccion() != null
            ? appointment.getCodigoTransaccion()
            : "—";

        return HTML_TEMPLATE
            .replace("{nombre}",             appointment.getNombreCliente())
            .replace("{idExterno}",          appointment.getIdExterno())
            .replace("{servicio}",           appointment.getService().getName())
            .replace("{fecha}",              fecha)
            .replace("{hora}",               hora)
            .replace("{monto}",              formatCLP(appointment.getMonto()))
            .replace("{codigoTransaccion}",  codigo)
            .replace("{anio}",               String.valueOf(java.time.Year.now().getValue()));
    }

    private static String formatCLP(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(monto);
    }
}
