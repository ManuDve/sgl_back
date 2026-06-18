package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.LegalService;
import cl.sgl.entity.TipoEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para WhatsAppService.
 *
 * Historia: SGL-034 NOTIF-WA-01, SGL-028 AG-WA-CONF
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppService Tests")
class WhatsAppServiceTest {

    @Mock
    private NotificationLogService notificationLogService;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        LegalService servicio = LegalService.builder()
            .id(1L)
            .name("Divorcio Contencioso")
            .price(new BigDecimal("500000"))
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        appointment = Appointment.builder()
            .id(1L)
            .idExterno("AG-2026-0001")
            .nombreCliente("Juan Pérez")
            .email("juan@example.com")
            .telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 6, 20))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ── sendPaymentConfirmedWhatsApp ──────────────────────────────────────────

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp configurado sin payment SID envía texto libre y retorna true")
    void testSendPaymentConfirmedWhatsApp_Configurado_EnviaYRetornaTrue() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSendFreeform(anyString(), anyString());

        boolean result = service.sendPaymentConfirmedWhatsApp(appointment);

        assertTrue(result);
        verify(service).doSendFreeform(eq("+56912345678"), anyString());
        verify(service, never()).doSendWithTemplate(anyString(), anyString(), anyString());
        verify(notificationLogService).logSuccess(eq(1L), eq(TipoEmail.CONFIRMACION_CLIENTE),
            eq(NotificationLogService.CANAL_WHATSAPP), eq("+56912345678"));
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp no configurado retorna false sin enviar")
    void testSendPaymentConfirmedWhatsApp_NoConfigurado_RetornaFalse() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        boolean result = service.sendPaymentConfirmedWhatsApp(appointment);

        assertFalse(result);
        verifyNoInteractions(notificationLogService);
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp falla en envío retorna false y registra error")
    void testSendPaymentConfirmedWhatsApp_FallaEnvio_RetornaFalseYRegistraError() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doThrow(new RuntimeException("timeout")).when(service).doSendFreeform(anyString(), anyString());

        boolean result = service.sendPaymentConfirmedWhatsApp(appointment);

        assertFalse(result);
        verify(notificationLogService).logFailure(eq(1L), eq(TipoEmail.CONFIRMACION_CLIENTE),
            eq(NotificationLogService.CANAL_WHATSAPP), eq("+56912345678"), contains("timeout"));
        verify(notificationLogService, never()).logSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp mensaje texto libre contiene idExterno, fecha y hora")
    void testSendPaymentConfirmedWhatsApp_MensajeContieneIdFechaHora() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSendFreeform(anyString(), anyString());

        service.sendPaymentConfirmedWhatsApp(appointment);

        verify(service).doSendFreeform(anyString(), argThat(body ->
            body.contains("AG-2026-0001") &&
            body.contains("10:00") &&
            body.contains("confirmado")
        ));
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp con appointment SID (sin payment SID) usa texto libre")
    void testSendPaymentConfirmedWhatsApp_SoloAppointmentSid_UsaTextoLibre() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "HXappointment", notificationLogService));
        doNothing().when(service).doSendFreeform(anyString(), anyString());

        service.sendPaymentConfirmedWhatsApp(appointment);

        verify(service).doSendFreeform(anyString(), anyString());
        verify(service, never()).doSendWithTemplate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp con payment SID usa doSendWithTemplate")
    void testSendPaymentConfirmedWhatsApp_ConPaymentSid_UsaDoSendWithTemplate() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "", "HXpayment123", notificationLogService));
        doNothing().when(service).doSendWithTemplate(anyString(), anyString(), anyString());

        boolean result = service.sendPaymentConfirmedWhatsApp(appointment);

        assertTrue(result);
        verify(service).doSendWithTemplate(eq("+56912345678"), eq("HXpayment123"), anyString());
        verify(service, never()).doSendFreeform(anyString(), anyString());
    }

    @Test
    @DisplayName("sendPaymentConfirmedWhatsApp con payment SID variables JSON contienen idExterno, fecha y hora")
    void testSendPaymentConfirmedWhatsApp_ConPaymentSid_VariablesContienenDatos() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "", "HXpayment123", notificationLogService));
        doNothing().when(service).doSendWithTemplate(anyString(), anyString(), anyString());

        service.sendPaymentConfirmedWhatsApp(appointment);

        verify(service).doSendWithTemplate(anyString(), anyString(), argThat(vars ->
            vars.contains("AG-2026-0001") &&
            vars.contains("20/6") &&
            vars.contains("10:00")
        ));
    }

    // ── sendConfirmationWhatsApp — flujo general ──────────────────────────────

    @Test
    @DisplayName("sendConfirmationWhatsApp configurado envía mensaje y retorna true")
    void testSendConfirmationWhatsApp_Configurado_EnviaYRetornaTrue() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSend(anyString(), anyString());

        boolean result = service.sendConfirmationWhatsApp(appointment);

        assertTrue(result);
        verify(service).doSend(eq("+56912345678"), anyString());
        verify(notificationLogService).logSuccess(eq(1L), eq(TipoEmail.CONFIRMACION_CLIENTE),
            eq(NotificationLogService.CANAL_WHATSAPP), eq("+56912345678"));
    }

    @Test
    @DisplayName("sendConfirmationWhatsApp no configurado retorna false sin enviar")
    void testSendConfirmationWhatsApp_NoConfigurado_RetornaFalse() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        boolean result = service.sendConfirmationWhatsApp(appointment);

        assertFalse(result);
        verifyNoInteractions(notificationLogService);
    }

    @Test
    @DisplayName("sendConfirmationWhatsApp falla en doSend retorna false y registra error")
    void testSendConfirmationWhatsApp_FallaEnvio_RetornaFalseYRegistraError() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doThrow(new RuntimeException("Twilio connection error")).when(service).doSend(anyString(), anyString());

        boolean result = service.sendConfirmationWhatsApp(appointment);

        assertFalse(result);
        verify(notificationLogService).logFailure(eq(1L), eq(TipoEmail.CONFIRMACION_CLIENTE),
            eq(NotificationLogService.CANAL_WHATSAPP), eq("+56912345678"),
            contains("Twilio connection error"));
        verify(notificationLogService, never()).logSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendConfirmationWhatsApp normaliza telefono sin codigo de pais antes de enviar")
    void testSendConfirmationWhatsApp_TelefonoSinCodigo_SeNormalizaAntesDeEnviar() {
        appointment.setTelefono("988273166");
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSend(anyString(), anyString());

        service.sendConfirmationWhatsApp(appointment);

        verify(service).doSend(eq("+56988273166"), anyString());
        verify(notificationLogService).logSuccess(any(), any(), any(), eq("+56988273166"));
    }

    // ── Content Template (TWILIO_CONTENT_SID) ────────────────────────────────

    @Test
    @DisplayName("sendConfirmationWhatsApp con ContentSid pasa variables JSON como payload")
    void testSendConfirmationWhatsApp_ConTemplateSid_UsaVariablesJSON() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "HXb5b62575e6e4ff6129ad7c8efe1f983e", notificationLogService));
        doNothing().when(service).doSend(anyString(), anyString());

        service.sendConfirmationWhatsApp(appointment);

        verify(service).doSend(eq("+56912345678"), argThat(payload ->
            payload.startsWith("{") &&
            payload.contains("\"1\"") && payload.contains("Juan Pérez") &&
            payload.contains("\"2\"") && payload.contains("AG-2026-0001") &&
            payload.contains("\"3\"") && payload.contains("Divorcio Contencioso") &&
            payload.contains("\"4\"") &&
            payload.contains("\"5\"") && payload.contains("10:00")
        ));
    }

    @Test
    @DisplayName("sendConfirmationWhatsApp sin ContentSid pasa texto libre con datos del agendamiento")
    void testSendConfirmationWhatsApp_SinTemplateSid_UsaTextoLibre() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSend(anyString(), anyString());

        service.sendConfirmationWhatsApp(appointment);

        verify(service).doSend(anyString(), argThat(body ->
            body.contains("Juan Pérez") &&
            body.contains("AG-2026-0001") &&
            body.contains("Divorcio Contencioso") &&
            body.contains("10:00")
        ));
    }

    @Test
    @DisplayName("sendConfirmationWhatsApp template genera fecha en formato d/M y hora HH:mm")
    void testSendConfirmationWhatsApp_Template_FechaYHoraEnFormatoCorrecto() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "HXtest", notificationLogService));
        doNothing().when(service).doSend(anyString(), anyString());

        service.sendConfirmationWhatsApp(appointment); // fecha: 2026-06-20, hora: 10:00

        verify(service).doSend(anyString(), argThat(payload ->
            payload.contains("20/6") &&
            payload.contains("10:00")
        ));
    }

    // ── sendMenuMessage ───────────────────────────────────────────────────────

    @Test
    @DisplayName("sendMenuMessage no configurado retorna false sin enviar")
    void testSendMenuMessage_NoConfigurado_RetornaFalse() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        boolean result = service.sendMenuMessage("+56912345678");

        assertFalse(result);
        verifyNoInteractions(notificationLogService);
    }

    @Test
    @DisplayName("sendMenuMessage configurado sin menu SID envía texto libre y retorna true")
    void testSendMenuMessage_Configurado_SinMenuSid_UsaTextoLibre() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doNothing().when(service).doSendFreeform(anyString(), anyString());

        boolean result = service.sendMenuMessage("+56912345678");

        assertTrue(result);
        verify(service).doSendFreeform(eq("+56912345678"), eq(WhatsAppService.MENU_TEXT));
        verify(service, never()).doSendWithTemplate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendMenuMessage configurado con menu SID usa doSendWithTemplate")
    void testSendMenuMessage_Configurado_ConMenuSid_UsaTemplate() {
        WhatsAppService service = spy(
            new WhatsAppService("+14155238886", true, "", "", "HXmenu123", notificationLogService));
        doNothing().when(service).doSendWithTemplate(anyString(), anyString(), anyString());

        boolean result = service.sendMenuMessage("+56912345678");

        assertTrue(result);
        verify(service).doSendWithTemplate(eq("+56912345678"), eq("HXmenu123"), eq("{}"));
        verify(service, never()).doSendFreeform(anyString(), anyString());
    }

    @Test
    @DisplayName("sendMenuMessage falla en envío retorna false")
    void testSendMenuMessage_FallaEnvio_RetornaFalse() {
        WhatsAppService service = spy(new WhatsAppService("+14155238886", true, notificationLogService));
        doThrow(new RuntimeException("timeout")).when(service).doSendFreeform(anyString(), anyString());

        boolean result = service.sendMenuMessage("+56912345678");

        assertFalse(result);
        verifyNoInteractions(notificationLogService);
    }

    @Test
    @DisplayName("sendMenuMessage texto libre contiene las 3 opciones del menú")
    void testSendMenuMessage_TextoLibre_ContieneTresOpciones() {
        assertTrue(WhatsAppService.MENU_TEXT.contains("1. Consultar mi cita"));
        assertTrue(WhatsAppService.MENU_TEXT.contains("2. Reagendar mi cita"));
        assertTrue(WhatsAppService.MENU_TEXT.contains("3. Cancelar mi cita"));
        assertTrue(WhatsAppService.MENU_TEXT.contains("SGL"));
    }

    // ── formatPhone ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("formatPhone numero con codigo de pais retorna igual")
    void testFormatPhone_ConCodigo_RetornaMismo() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        assertEquals("+56912345678", service.formatPhone("+56912345678"));
    }

    @Test
    @DisplayName("formatPhone numero de 9 digitos agrega +56")
    void testFormatPhone_SinCodigo_AgregaCodigo() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        assertEquals("+56912345678", service.formatPhone("912345678"));
    }

    @Test
    @DisplayName("formatPhone numero con espacios elimina espacios y normaliza")
    void testFormatPhone_ConEspacios_EliminaEspacios() {
        WhatsAppService service = new WhatsAppService("+14155238886", false, notificationLogService);

        assertEquals("+56912345678", service.formatPhone("+56 9 1234 5678"));
    }
}
