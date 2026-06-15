package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.EmailRetryQueue;
import cl.sgl.entity.EstadoRetry;
import cl.sgl.entity.LegalService;
import cl.sgl.entity.ReminderTipo;
import cl.sgl.entity.TipoEmail;
import cl.sgl.repository.EmailRetryQueueRepository;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para EmailService.
 * Historias: SGL-033 NOTIF-EMAIL-01, SGL-038 NOTIF-RETRY, SGL-040 NOTIF-AUDIT
 */
@DisplayName("EmailService Tests")
class EmailServiceTest {

    private MailtrapClient            mockClient;
    private EmailRetryQueueRepository mockRetryQueue;
    private NotificationLogService    mockNotifLog;
    private EmailService              emailService;
    private Appointment               appointment;

    @BeforeEach
    void setUp() {
        mockClient    = mock(MailtrapClient.class);
        mockRetryQueue = mock(EmailRetryQueueRepository.class);
        mockNotifLog   = mock(NotificationLogService.class);
        emailService  = new EmailService(mockClient, "no-reply@sgl.cl", "admin@test.cl",
                new EmailTemplateBuilder(), mockRetryQueue, mockNotifLog);

        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        appointment = Appointment.builder()
            .id(1L)
            .idExterno("AG-ABCD-0001")
            .nombreCliente("Juan Pérez")
            .email("juan.perez@example.cl")
            .telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("sendConfirmationEmail llama a mailtrapClient.send con los datos correctos")
    void testSendConfirmationEmail_DatosCorrectos() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendConfirmationEmail(appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());

        MailtrapMail mail = captor.getValue();
        assertEquals("juan.perez@example.cl", mail.getTo().get(0).getEmail());
        assertEquals("no-reply@sgl.cl",        mail.getFrom().getEmail());
        assertEquals("Estudio Jurídico SGL",  mail.getFrom().getName());
        assertTrue(mail.getSubject().contains("Pago confirmado"));
        assertTrue(mail.getHtml().contains("AG-ABCD-0001"),  "El HTML debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("Juan Pérez"),    "El HTML debe incluir el nombre del cliente");
        assertTrue(mail.getHtml().contains("Divorcio"),      "El HTML debe incluir el nombre del servicio");
    }

    @Test
    @DisplayName("sendConfirmationEmail no lanza excepción si el cliente falla")
    void testSendConfirmationEmail_ErrorNoLanzaExcepcion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("Mailtrap no disponible"));

        assertDoesNotThrow(() -> emailService.sendConfirmationEmail(appointment));
        verify(mockClient).send(any(MailtrapMail.class));
    }

    // ── SGL-036 NOTIF-ADMIN-NEW ───────────────────────────────────────

    @Test
    @DisplayName("sendAdminNewAppointmentEmail envía al adminEmail con asunto e idExterno correctos")
    void testSendAdminNewAppointmentEmail_DatosCorrectos() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendAdminNewAppointmentEmail(appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());

        MailtrapMail mail = captor.getValue();
        assertEquals("admin@test.cl",   mail.getTo().get(0).getEmail());
        assertEquals("no-reply@sgl.cl", mail.getFrom().getEmail());
        assertTrue(mail.getSubject().contains("AG-ABCD-0001"), "El asunto debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("AG-ABCD-0001"),    "El HTML debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("Juan Pérez"),      "El HTML debe incluir el nombre del cliente");
        assertTrue(mail.getHtml().contains("Divorcio"),        "El HTML debe incluir el nombre del servicio");
    }

    @Test
    @DisplayName("sendAdminNewAppointmentEmail no lanza excepción si el cliente falla")
    void testSendAdminNewAppointmentEmail_ErrorNoLanzaExcepcion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("Mailtrap no disponible"));

        assertDoesNotThrow(() -> emailService.sendAdminNewAppointmentEmail(appointment));
        verify(mockClient).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("sendAdminNewAppointmentEmail omite el envío si adminEmail está vacío")
    void testSendAdminNewAppointmentEmail_SkipSiAdminEmailVacio() throws Exception {
        EmailService serviceConEmailVacio = new EmailService(
                mockClient, "no-reply@sgl.cl", "", new EmailTemplateBuilder(), mockRetryQueue, mockNotifLog);

        serviceConEmailVacio.sendAdminNewAppointmentEmail(appointment);

        verify(mockClient, never()).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("sendAdminNewAppointmentEmail incluye el bloque de descripción cuando está presente")
    void testSendAdminNewAppointmentEmail_ConDescripcion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        Appointment conDescripcion = Appointment.builder()
            .id(2L).idExterno("AG-ABCD-0002")
            .nombreCliente("Juan Pérez").email("juan.perez@example.cl").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.PENDING)
            .descripcion("Necesito asesoría sobre divorcio de mutuo acuerdo.")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        emailService.sendAdminNewAppointmentEmail(conDescripcion);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertTrue(captor.getValue().getHtml().contains("Necesito asesoría sobre divorcio"),
            "El HTML debe incluir la descripción del caso");
    }

    // ── SGL-035 NOTIF-REMIND ──────────────────────────────────────────────

    @Test
    @DisplayName("sendReminderEmail REMIND_24H envía con asunto 'mañana' al email del cliente")
    void testSendReminderEmail_24h_DatosCorrectos() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_24H);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        MailtrapMail mail = captor.getValue();
        assertEquals("juan.perez@example.cl", mail.getTo().get(0).getEmail());
        assertTrue(mail.getSubject().contains("mañana"),       "asunto 24h debe mencionar 'mañana'");
        assertTrue(mail.getSubject().contains("AG-ABCD-0001"), "asunto debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("programada para mañana"), "html 24h debe decir 'mañana'");
    }

    @Test
    @DisplayName("sendReminderEmail REMIND_2H envía con asunto '2 horas' al email del cliente")
    void testSendReminderEmail_2h_DatosCorrectos() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_2H);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        MailtrapMail mail = captor.getValue();
        assertEquals("juan.perez@example.cl", mail.getTo().get(0).getEmail());
        assertTrue(mail.getSubject().contains("2 horas"),      "asunto 2h debe mencionar '2 horas'");
        assertTrue(mail.getSubject().contains("AG-ABCD-0001"), "asunto debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("próximas 2 horas"), "html 2h debe decir '2 horas'");
    }

    @Test
    @DisplayName("sendReminderEmail retorna false y no lanza excepción si el cliente de correo falla")
    void testSendReminderEmail_ErrorRetornaFalse() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("Mailtrap no disponible"));

        boolean resultado = emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_24H);

        assertFalse(resultado, "debe retornar false cuando el envío falla");
        verify(mockClient).send(any(MailtrapMail.class));
    }

    @Test
    @DisplayName("sendConfirmationEmail incluye el código de transacción cuando está presente")
    void testSendConfirmationEmail_ConCodigoTransaccion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        Appointment conCodigo = Appointment.builder()
            .id(3L).idExterno("AG-ABCD-0003")
            .nombreCliente("Juan Pérez").email("juan.perez@example.cl").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.CONFIRMED)
            .codigoTransaccion("TXN-987654321")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        emailService.sendConfirmationEmail(conCodigo);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertTrue(captor.getValue().getHtml().contains("TXN-987654321"),
            "El HTML debe incluir el código de transacción");
    }

    // ── SGL-038 NOTIF-RETRY — encolar en retry queue al fallar ────────────

    @Test
    @DisplayName("sendConfirmationEmail encola en retry queue con tipo CONFIRMACION_CLIENTE si Mailtrap falla")
    void testSendConfirmationEmail_ErrorEncolarEnRetryQueue() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));

        emailService.sendConfirmationEmail(appointment);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(mockRetryQueue).save(captor.capture());
        EmailRetryQueue encolado = captor.getValue();
        assertEquals(TipoEmail.CONFIRMACION_CLIENTE, encolado.getTipoEmail());
        assertEquals(1L,                             encolado.getAppointmentId());
        assertEquals(0,                              encolado.getIntentos());
        assertEquals(EstadoRetry.PENDIENTE,          encolado.getEstado());
        assertNotNull(encolado.getProximoIntento());
    }

    @Test
    @DisplayName("sendAdminNewAppointmentEmail encola en retry queue con tipo NOTIF_ADMIN si Mailtrap falla")
    void testSendAdminNewAppointmentEmail_ErrorEncolarEnRetryQueue() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));

        emailService.sendAdminNewAppointmentEmail(appointment);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(mockRetryQueue).save(captor.capture());
        EmailRetryQueue encolado = captor.getValue();
        assertEquals(TipoEmail.NOTIF_ADMIN, encolado.getTipoEmail());
        assertEquals(1L,                    encolado.getAppointmentId());
        assertEquals(EstadoRetry.PENDIENTE, encolado.getEstado());
    }

    @Test
    @DisplayName("sendReminderEmail encola en retry queue con tipo REMINDER_24H si Mailtrap falla")
    void testSendReminderEmail_ErrorEncolarEnRetryQueue() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));

        emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_24H);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(mockRetryQueue).save(captor.capture());
        EmailRetryQueue encolado = captor.getValue();
        assertEquals(TipoEmail.REMINDER_24H, encolado.getTipoEmail());
        assertEquals(1L,                     encolado.getAppointmentId());
        assertEquals(EstadoRetry.PENDIENTE,  encolado.getEstado());
    }

    @Test
    @DisplayName("sendReminderEmail encola con tipo REMINDER_2H cuando falla con tipo REMIND_2H")
    void testSendReminderEmail_Reminder2h_ErrorEncolarConTipoReminder2h() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));

        emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_2H);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(mockRetryQueue).save(captor.capture());
        assertEquals(TipoEmail.REMINDER_2H, captor.getValue().getTipoEmail());
    }

    @Test
    @DisplayName("sendConfirmationEmail encola con ultimoError null cuando la excepción no tiene mensaje")
    void testSendConfirmationEmail_ExcepcionSinMensaje_EncolarConUltimoErrorNulo() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException((String) null));

        emailService.sendConfirmationEmail(appointment);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(mockRetryQueue).save(captor.capture());
        assertNull(captor.getValue().getUltimoError());
    }

    @Test
    @DisplayName("sendConfirmationEmail no lanza excepción si el encolado en retry queue falla")
    void testSendConfirmationEmail_ErrorDeEncolado_NoLanzaExcepcion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));
        when(mockRetryQueue.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> emailService.sendConfirmationEmail(appointment));
    }

    // ── SGL-038 NOTIF-RETRY — retryEmail ─────────────────────────────────

    @Test
    @DisplayName("retryEmail CONFIRMACION_CLIENTE envía email de confirmación al cliente")
    void testRetryEmail_ConfirmacionCliente_EnviaEmailAlCliente() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.CONFIRMACION_CLIENTE);

        emailService.retryEmail(entry, appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertEquals("juan.perez@example.cl", captor.getValue().getTo().get(0).getEmail());
        assertTrue(captor.getValue().getSubject().contains("Pago confirmado"));
    }

    @Test
    @DisplayName("retryEmail NOTIF_ADMIN envía email de notificación al admin")
    void testRetryEmail_NotifAdmin_EnviaEmailAlAdmin() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.NOTIF_ADMIN);

        emailService.retryEmail(entry, appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertEquals("admin@test.cl", captor.getValue().getTo().get(0).getEmail());
        assertTrue(captor.getValue().getSubject().contains("AG-ABCD-0001"));
    }

    @Test
    @DisplayName("retryEmail REMINDER_24H envía recordatorio con asunto 'mañana'")
    void testRetryEmail_Reminder24h_EnviaEmailConAsuntoManana() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.REMINDER_24H);

        emailService.retryEmail(entry, appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertTrue(captor.getValue().getSubject().contains("mañana"));
    }

    @Test
    @DisplayName("retryEmail REMINDER_2H envía recordatorio con asunto '2 horas'")
    void testRetryEmail_Reminder2h_EnviaEmailConAsunto2Horas() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.REMINDER_2H);

        emailService.retryEmail(entry, appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());
        assertTrue(captor.getValue().getSubject().contains("2 horas"));
    }

    @Test
    @DisplayName("retryEmail lanza excepción si Mailtrap falla, sin silenciarla")
    void testRetryEmail_LanzaExcepcionSiMailtrapFalla() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.CONFIRMACION_CLIENTE);

        assertThrows(Exception.class, () -> emailService.retryEmail(entry, appointment));
        verify(mockRetryQueue, never()).save(any());
    }

    // ── SGL-040 NOTIF-AUDIT — registro en notification_log ────────────────

    @Test
    @DisplayName("sendConfirmationEmail exitoso registra notificación como ENVIADO")
    void testSendConfirmationEmail_ExitosoRegistraNotificacionEnviada() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendConfirmationEmail(appointment);

        verify(mockNotifLog).logSuccess(1L, TipoEmail.CONFIRMACION_CLIENTE, "juan.perez@example.cl");
        verify(mockNotifLog, never()).logFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendConfirmationEmail fallido registra notificación como FALLIDO antes de encolar")
    void testSendConfirmationEmail_FallidoRegistraNotificacionFallida() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));

        emailService.sendConfirmationEmail(appointment);

        verify(mockNotifLog).logFailure(1L, TipoEmail.CONFIRMACION_CLIENTE, "juan.perez@example.cl", "timeout");
        verify(mockNotifLog, never()).logSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("sendAdminNewAppointmentEmail exitoso registra notificación al adminEmail")
    void testSendAdminNewAppointmentEmail_ExitosoRegistraNotificacionAdmin() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendAdminNewAppointmentEmail(appointment);

        verify(mockNotifLog).logSuccess(1L, TipoEmail.NOTIF_ADMIN, "admin@test.cl");
    }

    @Test
    @DisplayName("sendReminderEmail REMIND_24H exitoso registra notificación con tipo REMINDER_24H")
    void testSendReminderEmail_24h_ExitosoRegistraNotificacion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendReminderEmail(appointment, ReminderTipo.REMIND_24H);

        verify(mockNotifLog).logSuccess(1L, TipoEmail.REMINDER_24H, "juan.perez@example.cl");
    }

    @Test
    @DisplayName("retryEmail exitoso registra notificación CONFIRMACION_CLIENTE como ENVIADO")
    void testRetryEmail_ExitosoRegistraNotificacionEnviada() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.CONFIRMACION_CLIENTE);

        emailService.retryEmail(entry, appointment);

        verify(mockNotifLog).logSuccess(1L, TipoEmail.CONFIRMACION_CLIENTE, "juan.perez@example.cl");
        verify(mockNotifLog, never()).logFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("retryEmail fallido registra notificación como FALLIDO y re-lanza la excepción")
    void testRetryEmail_FallidoRegistraNotificacionYRelanza() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("timeout"));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.CONFIRMACION_CLIENTE);

        assertThrows(Exception.class, () -> emailService.retryEmail(entry, appointment));

        verify(mockNotifLog).logFailure(1L, TipoEmail.CONFIRMACION_CLIENTE, "juan.perez@example.cl", "timeout");
        verify(mockNotifLog, never()).logSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("retryEmail NOTIF_ADMIN usa adminEmail como destinatario en el log")
    void testRetryEmail_NotifAdmin_UsaAdminEmailComoDestinatario() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));
        EmailRetryQueue entry = buildRetryEntry(TipoEmail.NOTIF_ADMIN);

        emailService.retryEmail(entry, appointment);

        verify(mockNotifLog).logSuccess(1L, TipoEmail.NOTIF_ADMIN, "admin@test.cl");
    }

    private EmailRetryQueue buildRetryEntry(TipoEmail tipo) {
        return EmailRetryQueue.builder()
            .id(1L).appointmentId(1L).tipoEmail(tipo).intentos(1)
            .proximoIntento(LocalDateTime.now()).estado(EstadoRetry.PENDIENTE)
            .build();
    }
}
