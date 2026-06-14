package cl.sgl.service;

import cl.sgl.entity.Appointment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el HTML de los emails transaccionales del sistema.
 *
 * Todos los templates comparten el mismo header (logo SGL dorado) y footer,
 * producidos por wrap(). Cada método público construye el cuerpo específico
 * y lo pasa a wrap().
 *
 * Historia: SGL-039 NOTIF-TEMPL
 */
@Component
public class EmailTemplateBuilder {

    // ── CSS compartido por todos los templates ─────────────────────────

    private static final String CSS =
        "body{margin:0;padding:0;background:#f0f0f0;font-family:Arial,sans-serif;}" +
        ".wrap{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;" +
              "overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.12);}" +
        ".hdr{background:#0A0A0A;padding:24px 32px;}" +
        ".logo{color:#C9A84C;font-size:22px;font-weight:bold;margin:0;letter-spacing:2px;}" +
        ".sub{color:#6B6B6B;font-size:12px;margin:4px 0 0;}" +
        ".bdy{padding:32px;}" +
        ".box{background:#f9f9f9;border-left:4px solid #C9A84C;border-radius:4px;" +
             "padding:20px 24px;margin:0 0 20px;}" +
        ".box table{width:100%;border-collapse:collapse;}" +
        ".box tr td{padding:8px 0;border-bottom:1px solid #eaeaea;font-size:14px;color:#333;}" +
        ".box tr:last-child td{border-bottom:none;}" +
        ".lbl{color:#888;width:40%;}" +
        ".val{font-weight:bold;text-align:right;}" +
        ".id{background:#0A0A0A;color:#C9A84C;font-weight:bold;font-size:14px;" +
            "display:inline-block;padding:3px 10px;border-radius:4px;}" +
        ".badge{background:#C9A84C20;color:#C9A84C;border:1px solid #C9A84C50;" +
               "font-size:11px;font-weight:bold;padding:2px 8px;border-radius:12px;}" +
        ".desc{background:#fff8e7;border-left:3px solid #C9A84C;border-radius:3px;" +
              "padding:12px 16px;margin:0 0 20px;font-size:13px;color:#444;line-height:1.6;}" +
        ".note{font-size:13px;color:#555;line-height:1.6;margin:0 0 14px;}" +
        ".ftr{background:#1A1A1A;padding:18px 32px;text-align:center;}" +
        ".ftr p{color:#6B6B6B;font-size:11px;margin:3px 0;}";

    // ── Template base ──────────────────────────────────────────────────

    /**
     * Envuelve un bloque de body con el header SGL dorado y el footer estándar.
     *
     * @param subtitle  línea bajo el logo (ej: "Estudio Jurídico · Santiago, Chile")
     * @param body      HTML del cuerpo específico del template
     * @param footerNote nota adicional al pie (ej: aviso de no responder)
     */
    private static String wrap(String subtitle, String body, String footerNote) {
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"/>" +
               "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
               "<style>" + CSS + "</style></head><body><div class=\"wrap\">" +
               "<div class=\"hdr\"><p class=\"logo\">SGL</p>" +
               "<p class=\"sub\">" + subtitle + "</p></div>" +
               "<div class=\"bdy\">" + body + "</div>" +
               "<div class=\"ftr\">" +
               "<p>&copy; " + Year.now().getValue() + " SGL Estudio Jurídico &middot; Santiago, Chile</p>" +
               "<p>" + footerNote + "</p></div>" +
               "</div></body></html>";
    }

    // ── Templates públicos ─────────────────────────────────────────────

    /**
     * Email de confirmación de pago al cliente.
     * Historias: SGL-033 NOTIF-EMAIL-01, SGL-037 NOTIF-ID
     */
    public String buildConfirmationEmail(Appointment a) {
        String codigo = a.getCodigoTransaccion() != null ? a.getCodigoTransaccion() : "—";

        String body =
            "<p style=\"font-size:16px;color:#222;margin:0 0 14px;\">Estimado/a " +
            "<strong>" + a.getNombreCliente() + "</strong>,</p>" +
            "<p class=\"note\">Tu pago fue aprobado y tu consulta ha quedado " +
            "<strong style=\"color:#C9A84C;\">confirmada</strong>. Aquí están los detalles:</p>" +
            "<div class=\"box\"><table>" +
            "<tr><td class=\"lbl\">N° de cita</td>" +
                "<td class=\"val\"><span class=\"id\">" + a.getIdExterno() + "</span></td></tr>" +
            "<tr><td class=\"lbl\">Servicio</td>" +
                "<td class=\"val\">" + a.getService().getName() + "</td></tr>" +
            "<tr><td class=\"lbl\">Fecha</td>" +
                "<td class=\"val\">" + formatFecha(a.getFecha()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Hora</td>" +
                "<td class=\"val\">" + formatHora(a.getHora()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Monto pagado</td>" +
                "<td class=\"val\" style=\"color:#C9A84C;\">" + formatCLP(a.getMonto()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Código de transacción</td>" +
                "<td class=\"val\" style=\"font-family:monospace;\">" + codigo + "</td></tr>" +
            "</table></div>" +
            "<p class=\"note\">Si necesitas cancelar o reagendar tu cita, " +
            "contáctanos con al menos 24 horas de anticipación.</p>" +
            "<p class=\"note\">¿Tienes dudas? Escríbenos a " +
            "<a href=\"mailto:contacto@sglabogados.cl\" style=\"color:#C9A84C;\">" +
            "contacto@sglabogados.cl</a></p>";

        return wrap(
            "Estudio Jurídico · Santiago, Chile",
            body,
            "Este mensaje fue generado automáticamente, no respondas a este correo."
        );
    }

    /**
     * Notificación interna al admin cuando se crea un nuevo agendamiento.
     * Historia: SGL-036 NOTIF-ADMIN-NEW
     */
    public String buildAdminNotificationEmail(Appointment a) {
        String descripcionBlock = (a.getDescripcion() != null && !a.getDescripcion().isBlank())
            ? "<p style=\"font-size:13px;color:#555;font-weight:bold;margin:0 0 6px;\">" +
              "Descripción del caso:</p><div class=\"desc\">" + a.getDescripcion() + "</div>"
            : "";

        String body =
            "<p style=\"font-size:16px;color:#222;margin:0 0 8px;\">Nueva consulta agendada</p>" +
            "<p style=\"margin:0 0 20px;\"><span class=\"id\">" + a.getIdExterno() + "</span>" +
            "&nbsp;&nbsp;<span class=\"badge\">PENDIENTE</span></p>" +
            "<p class=\"note\">Se registró un nuevo agendamiento pendiente de pago:</p>" +
            "<div class=\"box\"><table>" +
            "<tr><td class=\"lbl\">Cliente</td><td class=\"val\">" + a.getNombreCliente() + "</td></tr>" +
            "<tr><td class=\"lbl\">Email</td><td class=\"val\">" + a.getEmail() + "</td></tr>" +
            "<tr><td class=\"lbl\">Teléfono</td><td class=\"val\">" + a.getTelefono() + "</td></tr>" +
            "</table></div>" +
            "<div class=\"box\"><table>" +
            "<tr><td class=\"lbl\">Servicio</td><td class=\"val\">" + a.getService().getName() + "</td></tr>" +
            "<tr><td class=\"lbl\">Fecha</td><td class=\"val\">" + formatFecha(a.getFecha()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Hora</td><td class=\"val\">" + formatHora(a.getHora()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Monto</td>" +
                "<td class=\"val\" style=\"color:#C9A84C;\">" + formatCLP(a.getMonto()) + "</td></tr>" +
            "</table></div>" +
            descripcionBlock +
            "<p class=\"note\">Accede al panel administrativo para confirmar el pago " +
            "cuando el cliente realice la transferencia.</p>";

        return wrap(
            "Panel Administrativo · Notificación Interna",
            body,
            "Este mensaje fue generado automáticamente."
        );
    }

    /**
     * Recordatorio de cita al cliente (24 h antes).
     * Historia: SGL-035 NOTIF-REMIND
     */
    public String buildReminderEmail(Appointment a) {
        String body =
            "<p style=\"font-size:16px;color:#222;margin:0 0 14px;\">Estimado/a " +
            "<strong>" + a.getNombreCliente() + "</strong>,</p>" +
            "<p class=\"note\">Te recordamos que tienes una consulta " +
            "<strong style=\"color:#C9A84C;\">programada para mañana</strong>.</p>" +
            "<div class=\"box\"><table>" +
            "<tr><td class=\"lbl\">N° de cita</td>" +
                "<td class=\"val\"><span class=\"id\">" + a.getIdExterno() + "</span></td></tr>" +
            "<tr><td class=\"lbl\">Servicio</td>" +
                "<td class=\"val\">" + a.getService().getName() + "</td></tr>" +
            "<tr><td class=\"lbl\">Fecha</td>" +
                "<td class=\"val\">" + formatFecha(a.getFecha()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Hora</td>" +
                "<td class=\"val\">" + formatHora(a.getHora()) + "</td></tr>" +
            "</table></div>" +
            "<p class=\"note\">Si necesitas cancelar o reagendar, " +
            "contáctanos con al menos 24 horas de anticipación.</p>" +
            "<p class=\"note\">¿Tienes dudas? Escríbenos a " +
            "<a href=\"mailto:contacto@sglabogados.cl\" style=\"color:#C9A84C;\">" +
            "contacto@sglabogados.cl</a></p>";

        return wrap(
            "Estudio Jurídico · Santiago, Chile",
            body,
            "Este mensaje fue generado automáticamente, no respondas a este correo."
        );
    }

    /**
     * Recordatorio de cita al cliente (2 h antes).
     * Historia: SGL-035 NOTIF-REMIND
     */
    public String buildReminder2hEmail(Appointment a) {
        String body =
            "<p style=\"font-size:16px;color:#222;margin:0 0 14px;\">Estimado/a " +
            "<strong>" + a.getNombreCliente() + "</strong>,</p>" +
            "<p class=\"note\">Te recordamos que tienes una consulta " +
            "<strong style=\"color:#C9A84C;\">programada en las próximas 2 horas</strong>.</p>" +
            "<div class=\"box\"><table>" +
            "<tr><td class=\"lbl\">N° de cita</td>" +
                "<td class=\"val\"><span class=\"id\">" + a.getIdExterno() + "</span></td></tr>" +
            "<tr><td class=\"lbl\">Servicio</td>" +
                "<td class=\"val\">" + a.getService().getName() + "</td></tr>" +
            "<tr><td class=\"lbl\">Fecha</td>" +
                "<td class=\"val\">" + formatFecha(a.getFecha()) + "</td></tr>" +
            "<tr><td class=\"lbl\">Hora</td>" +
                "<td class=\"val\">" + formatHora(a.getHora()) + "</td></tr>" +
            "</table></div>" +
            "<p class=\"note\">Por favor, preséntate con al menos 5 minutos de anticipación.</p>" +
            "<p class=\"note\">¿Tienes dudas? Escríbenos a " +
            "<a href=\"mailto:contacto@sglabogados.cl\" style=\"color:#C9A84C;\">" +
            "contacto@sglabogados.cl</a></p>";

        return wrap(
            "Estudio Jurídico · Santiago, Chile",
            body,
            "Este mensaje fue generado automáticamente, no respondas a este correo."
        );
    }

    // ── Helpers de formato (package-private para tests) ────────────────

    static String formatFecha(LocalDate fecha) {
        return fecha.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CL")));
    }

    static String formatHora(LocalTime hora) {
        return hora.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static String formatCLP(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(monto);
    }
}
