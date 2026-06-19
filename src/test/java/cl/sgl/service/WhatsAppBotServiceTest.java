package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.repository.AppointmentRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para WhatsAppBotService.
 * Historia: SGL-075 WA-CONSULT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppBotService Tests")
class WhatsAppBotServiceTest {

    @Mock private WhatsAppService       whatsAppService;
    @Mock private AppointmentRepository appointmentRepository;

    // TTL de 10 minutos inyectado manualmente (refleja el default del application.yml)
    private WhatsAppBotService botService;

    private static final String PHONE = "+56912345678";

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        botService = new WhatsAppBotService(whatsAppService, appointmentRepository, 10);
        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        appointment = Appointment.builder()
            .id(1L)
            .idExterno("AG-2026-0001")
            .nombreCliente("Juan Pérez")
            .email("juan@example.com")
            .telefono(PHONE)
            .service(servicio)
            .fecha(LocalDate.of(2026, 6, 20))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .reagendado(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleMessage sin sesión activa envía menú al recibir cualquier texto")
    void testHandleMessage_SinSesion_EnviaMenu() {
        botService.handleMessage(PHONE, "hola");

        verify(whatsAppService).sendMenuMessage(PHONE);
        verify(whatsAppService, never()).sendBotReply(any(), any());
    }

    @Test
    @DisplayName("handleMessage con body null envía menú")
    void testHandleMessage_BodyNull_EnviaMenu() {
        botService.handleMessage(PHONE, null);

        verify(whatsAppService).sendMenuMessage(PHONE);
    }

    // ── Opción 1: consultar cita ──────────────────────────────────────────────

    @Test
    @DisplayName("handleMessage con '1' solicita el ID de cita y activa sesión WAITING_FOR_APPOINTMENT_ID")
    void testHandleMessage_Opcion1_SolicitaId() {
        botService.handleMessage(PHONE, "1");

        verify(whatsAppService).sendBotReply(PHONE, WhatsAppBotService.MSG_ASK_ID);
        verify(whatsAppService, never()).sendMenuMessage(any());
    }

    @Test
    @DisplayName("handleMessage con '1' luego ID válido responde con detalles de la cita")
    void testHandleMessage_Opcion1_LuegoIdValido_RespondeDetalles() {
        when(appointmentRepository.findByIdExterno("AG-2026-0001")).thenReturn(Optional.of(appointment));

        botService.handleMessage(PHONE, "1");
        botService.handleMessage(PHONE, "AG-2026-0001");

        verify(whatsAppService).sendBotReply(eq(PHONE), argThat(msg ->
            msg.contains("AG-2026-0001") &&
            msg.contains("Divorcio Contencioso") &&
            msg.contains("10:00") &&
            msg.contains("Pendiente de pago")
        ));
    }

    @Test
    @DisplayName("handleMessage con '1' luego ID en minúsculas normaliza a mayúsculas para la búsqueda")
    void testHandleMessage_IdEnMinusculas_NormalizaAMayusculas() {
        when(appointmentRepository.findByIdExterno("AG-2026-0001")).thenReturn(Optional.of(appointment));

        botService.handleMessage(PHONE, "1");
        botService.handleMessage(PHONE, "ag-2026-0001");

        verify(appointmentRepository).findByIdExterno("AG-2026-0001");
    }

    @Test
    @DisplayName("handleMessage con '1' luego ID inexistente responde con mensaje de error")
    void testHandleMessage_Opcion1_IdInexistente_RespondeError() {
        when(appointmentRepository.findByIdExterno(any())).thenReturn(Optional.empty());

        botService.handleMessage(PHONE, "1");
        botService.handleMessage(PHONE, "AG-XXXX-9999");

        verify(whatsAppService).sendBotReply(eq(PHONE), argThat(msg ->
            msg.contains("AG-XXXX-9999") && msg.contains("No encontramos")
        ));
    }

    @Test
    @DisplayName("handleMessage después de consultar exitosa, nuevo mensaje vuelve al menú")
    void testHandleMessage_DespuesDeConsulta_VuelveAlMenu() {
        when(appointmentRepository.findByIdExterno("AG-2026-0001")).thenReturn(Optional.of(appointment));

        botService.handleMessage(PHONE, "1");
        botService.handleMessage(PHONE, "AG-2026-0001");
        botService.handleMessage(PHONE, "hola");

        verify(whatsAppService).sendMenuMessage(PHONE);
    }

    // ── Detalles de cita ──────────────────────────────────────────────────────

    @Test
    @DisplayName("buildDetailsMessage cita CONFIRMED muestra estado 'Confirmada'")
    void testBuildDetailsMessage_EstadoConfirmada() {
        appointment.setEstado(AppointmentStatus.CONFIRMED);

        String msg = botService.buildDetailsMessage(appointment);

        assertTrue(msg.contains("Confirmada"));
    }

    @Test
    @DisplayName("buildDetailsMessage cita CANCELLED muestra estado 'Cancelada'")
    void testBuildDetailsMessage_EstadoCancelada() {
        appointment.setEstado(AppointmentStatus.CANCELLED);

        String msg = botService.buildDetailsMessage(appointment);

        assertTrue(msg.contains("Cancelada"));
    }

    @Test
    @DisplayName("buildDetailsMessage cita reagendada muestra estado 'Reagendada'")
    void testBuildDetailsMessage_Reagendada() {
        appointment.setReagendado(true);

        String msg = botService.buildDetailsMessage(appointment);

        assertTrue(msg.contains("Reagendada"));
    }

    @Test
    @DisplayName("buildDetailsMessage incluye idExterno, servicio, fecha y hora")
    void testBuildDetailsMessage_IncluyeTodosLosCampos() {
        String msg = botService.buildDetailsMessage(appointment);

        assertTrue(msg.contains("AG-2026-0001"));
        assertTrue(msg.contains("Divorcio Contencioso"));
        assertTrue(msg.contains("10:00"));
        assertFalse(msg.isBlank());
    }
}
