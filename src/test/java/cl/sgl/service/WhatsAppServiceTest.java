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
 * Historia: SGL-034 NOTIF-WA-01
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
