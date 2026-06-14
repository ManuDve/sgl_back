package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para EmailTemplateBuilder.
 * Historia: SGL-039 NOTIF-TEMPL
 */
@DisplayName("EmailTemplateBuilder Tests")
class EmailTemplateBuilderTest {

    private EmailTemplateBuilder builder;
    private Appointment          appointment;

    @BeforeEach
    void setUp() {
        builder = new EmailTemplateBuilder();

        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        appointment = Appointment.builder()
            .id(1L).idExterno("AG-ABCD-0001")
            .nombreCliente("Juan Pérez").email("juan.perez@example.cl").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    // ── Estructura base compartida ────────────────────────────────────

    @Test
    @DisplayName("todos los templates incluyen el header SGL y el footer estándar")
    void testEstructuraBase_HeaderYFooterPresentes() {
        String confirmation = builder.buildConfirmationEmail(appointment);
        String admin        = builder.buildAdminNotificationEmail(appointment);
        String reminder     = builder.buildReminderEmail(appointment);

        for (String html : new String[]{confirmation, admin, reminder}) {
            assertTrue(html.contains("class=\"logo\">SGL"),  "debe incluir el logo SGL");
            assertTrue(html.contains("class=\"ftr\""),       "debe incluir el footer");
            assertTrue(html.contains("SGL Estudio Jurídico"), "debe incluir la marca en el footer");
        }
    }

    // ── buildConfirmationEmail ────────────────────────────────────────

    @Test
    @DisplayName("buildConfirmationEmail incluye idExterno, nombre, servicio, fecha y monto")
    void testBuildConfirmationEmail_ContenidoCompleto() {
        String html = builder.buildConfirmationEmail(appointment);

        assertTrue(html.contains("AG-ABCD-0001"),        "debe incluir el idExterno");
        assertTrue(html.contains("Juan Pérez"),           "debe incluir el nombre del cliente");
        assertTrue(html.contains("Divorcio Contencioso"), "debe incluir el servicio");
        assertTrue(html.contains("10 de julio de 2026"), "debe incluir la fecha formateada");
        assertTrue(html.contains("10:00"),                "debe incluir la hora");
    }

    @Test
    @DisplayName("buildConfirmationEmail muestra '—' cuando codigoTransaccion es null")
    void testBuildConfirmationEmail_CodigoTransaccionNulo() {
        String html = builder.buildConfirmationEmail(appointment); // sin codigoTransaccion

        assertTrue(html.contains("—"), "debe mostrar guion cuando el código es null");
    }

    @Test
    @DisplayName("buildConfirmationEmail muestra el código de transacción cuando está presente")
    void testBuildConfirmationEmail_ConCodigoTransaccion() {
        Appointment conCodigo = Appointment.builder()
            .id(2L).idExterno("AG-ABCD-0002")
            .nombreCliente("Juan Pérez").email("juan.perez@example.cl").telefono("+56912345678")
            .service(appointment.getService())
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.CONFIRMED)
            .codigoTransaccion("TXN-987654321")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        String html = builder.buildConfirmationEmail(conCodigo);

        assertTrue(html.contains("TXN-987654321"), "debe incluir el código de transacción");
        assertFalse(html.contains(">—<"),          "no debe mostrar el guion cuando hay código");
    }

    // ── buildAdminNotificationEmail ───────────────────────────────────

    @Test
    @DisplayName("buildAdminNotificationEmail incluye datos del cliente y del agendamiento")
    void testBuildAdminNotificationEmail_ContenidoCompleto() {
        String html = builder.buildAdminNotificationEmail(appointment);

        assertTrue(html.contains("AG-ABCD-0001"),          "debe incluir el idExterno");
        assertTrue(html.contains("Juan Pérez"),             "debe incluir el nombre");
        assertTrue(html.contains("juan.perez@example.cl"), "debe incluir el email del cliente");
        assertTrue(html.contains("+56912345678"),           "debe incluir el teléfono");
        assertTrue(html.contains("Divorcio Contencioso"),  "debe incluir el servicio");
        assertTrue(html.contains("PENDIENTE"),              "debe incluir el badge de estado");
    }

    @Test
    @DisplayName("buildAdminNotificationEmail incluye bloque de descripción cuando está presente")
    void testBuildAdminNotificationEmail_ConDescripcion() {
        Appointment conDesc = Appointment.builder()
            .id(3L).idExterno("AG-ABCD-0003")
            .nombreCliente("Juan Pérez").email("juan.perez@example.cl").telefono("+56912345678")
            .service(appointment.getService())
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.PENDING)
            .descripcion("Consulta urgente sobre divorcio de mutuo acuerdo.")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        String html = builder.buildAdminNotificationEmail(conDesc);

        assertTrue(html.contains("Consulta urgente sobre divorcio"), "debe incluir la descripción");
        assertTrue(html.contains("class=\"desc\""),                  "debe incluir el bloque .desc");
    }

    @Test
    @DisplayName("buildAdminNotificationEmail omite el bloque .desc cuando descripcion es null")
    void testBuildAdminNotificationEmail_SinDescripcion() {
        String html = builder.buildAdminNotificationEmail(appointment); // descripcion == null

        assertFalse(html.contains("class=\"desc\""),
            "no debe incluir el bloque .desc cuando no hay descripción");
    }

    // ── buildReminderEmail ────────────────────────────────────────────

    @Test
    @DisplayName("buildReminderEmail incluye nombre, idExterno, servicio y fecha")
    void testBuildReminderEmail_ContenidoCompleto() {
        String html = builder.buildReminderEmail(appointment);

        assertTrue(html.contains("Juan Pérez"),           "debe incluir el nombre del cliente");
        assertTrue(html.contains("AG-ABCD-0001"),         "debe incluir el idExterno");
        assertTrue(html.contains("Divorcio Contencioso"), "debe incluir el servicio");
        assertTrue(html.contains("10 de julio de 2026"), "debe incluir la fecha formateada");
        assertTrue(html.contains("programada para mañana"), "debe incluir el mensaje de recordatorio");
    }

    // ── buildReminder2hEmail ──────────────────────────────────────────────

    @Test
    @DisplayName("buildReminder2hEmail incluye nombre, idExterno, servicio, fecha y mensaje 2h")
    void testBuildReminder2hEmail_ContenidoCompleto() {
        String html = builder.buildReminder2hEmail(appointment);

        assertTrue(html.contains("Juan Pérez"),                      "debe incluir el nombre del cliente");
        assertTrue(html.contains("AG-ABCD-0001"),                    "debe incluir el idExterno");
        assertTrue(html.contains("Divorcio Contencioso"),            "debe incluir el servicio");
        assertTrue(html.contains("10 de julio de 2026"),            "debe incluir la fecha formateada");
        assertTrue(html.contains("próximas 2 horas"),               "debe incluir el mensaje de 2 horas");
        assertTrue(html.contains("class=\"logo\">SGL"),             "debe incluir el header SGL");
        assertTrue(html.contains("class=\"ftr\""),                  "debe incluir el footer");
    }
}
