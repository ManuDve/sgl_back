package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.EmailRetryQueue;
import cl.sgl.entity.EstadoRetry;
import cl.sgl.entity.LegalService;
import cl.sgl.entity.TipoEmail;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.EmailRetryQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para RetryScheduler.
 * Historia: SGL-038 NOTIF-RETRY
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetryScheduler Tests")
class RetrySchedulerTest {

    @Mock EmailRetryQueueRepository retryQueueRepository;
    @Mock AppointmentRepository     appointmentRepository;
    @Mock EmailService              emailService;

    RetryScheduler scheduler;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 14, 10, 0);

    @BeforeEach
    void setUp() {
        scheduler = new RetryScheduler(retryQueueRepository, appointmentRepository, emailService);
    }

    // ── processRetries ─────────────────────────────────────────────────────

    @Test
    @DisplayName("processRetries no hace nada cuando no hay entradas pendientes")
    void testProcessRetries_SinPendientes_NadaOcurre() throws Exception {
        when(retryQueueRepository.findByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntento(
                any(), any())).thenReturn(List.of());

        scheduler.processRetries();

        verify(appointmentRepository, never()).findById(any());
        verify(emailService, never()).retryEmail(any(), any());
        verify(retryQueueRepository, never()).save(any());
    }

    @Test
    @DisplayName("processRetries con entradas pendientes llama processEntry para cada una")
    void testProcessRetries_ConPendientes_LlamaProcessEntryParaCadaEntrada() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.CONFIRMACION_CLIENTE, 0);
        Appointment     appointment = buildAppointment(1L);
        when(retryQueueRepository.findByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntento(
                any(), any())).thenReturn(List.of(entry));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        scheduler.processRetries();

        verify(emailService).retryEmail(entry, appointment);
        verify(retryQueueRepository).save(any(EmailRetryQueue.class));
    }

    // ── processEntry — éxito ───────────────────────────────────────────────

    @Test
    @DisplayName("processEntry exitoso marca la entrada como ENVIADO e incrementa intentos")
    void testProcessEntry_ExitosoMarcaEnviado() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.CONFIRMACION_CLIENTE, 0);
        Appointment     appointment = buildAppointment(1L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        scheduler.processEntry(entry, NOW);

        verify(emailService).retryEmail(entry, appointment);
        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        assertEquals(EstadoRetry.ENVIADO, captor.getValue().getEstado());
        assertEquals(1, captor.getValue().getIntentos());
    }

    // ── processEntry — agendamiento no encontrado ──────────────────────────

    @Test
    @DisplayName("processEntry marca FALLIDO cuando el agendamiento no existe en BD")
    void testProcessEntry_AppointmentNoEncontradoMarcaFallido() throws Exception {
        EmailRetryQueue entry = buildEntry(99L, TipoEmail.CONFIRMACION_CLIENTE, 0);
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        scheduler.processEntry(entry, NOW);

        verify(emailService, never()).retryEmail(any(), any());
        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        assertEquals(EstadoRetry.FALLIDO, captor.getValue().getEstado());
        assertNotNull(captor.getValue().getUltimoError());
    }

    // ── processEntry — backoff exponencial ────────────────────────────────

    @Test
    @DisplayName("processEntry primer fallo incrementa intentos y aplica backoff de 30 min")
    void testProcessEntry_PrimerFalloBackoff30min() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.CONFIRMACION_CLIENTE, 0);
        Appointment     appointment = buildAppointment(1L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        doThrow(new RuntimeException("timeout")).when(emailService).retryEmail(any(), any());

        scheduler.processEntry(entry, NOW);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        EmailRetryQueue saved = captor.getValue();
        assertEquals(EstadoRetry.PENDIENTE, saved.getEstado());
        assertEquals(1, saved.getIntentos());
        assertEquals(NOW.plusMinutes(30), saved.getProximoIntento()); // 15 × 2^1
    }

    @Test
    @DisplayName("processEntry segundo fallo aplica backoff de 60 min")
    void testProcessEntry_SegundoFalloBackoff60min() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.NOTIF_ADMIN, 1);
        Appointment     appointment = buildAppointment(1L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        doThrow(new RuntimeException("timeout")).when(emailService).retryEmail(any(), any());

        scheduler.processEntry(entry, NOW);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        EmailRetryQueue saved = captor.getValue();
        assertEquals(EstadoRetry.PENDIENTE, saved.getEstado());
        assertEquals(2, saved.getIntentos());
        assertEquals(NOW.plusMinutes(60), saved.getProximoIntento()); // 15 × 2^2
    }

    @Test
    @DisplayName("processEntry tercer fallo supera MAX_ATTEMPTS y marca la entrada como FALLIDO")
    void testProcessEntry_TercerFalloMarcaFallido() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.REMINDER_24H, 2);
        Appointment     appointment = buildAppointment(1L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        doThrow(new RuntimeException("timeout")).when(emailService).retryEmail(any(), any());

        scheduler.processEntry(entry, NOW);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        EmailRetryQueue saved = captor.getValue();
        assertEquals(EstadoRetry.FALLIDO, saved.getEstado());
        assertEquals(3, saved.getIntentos());
    }

    @Test
    @DisplayName("processEntry con excepción sin mensaje guarda ultimoError como null")
    void testProcessEntry_ErrorConMensajeNulo_GuardaUltimoErrorNulo() throws Exception {
        EmailRetryQueue entry      = buildEntry(1L, TipoEmail.CONFIRMACION_CLIENTE, 0);
        Appointment     appointment = buildAppointment(1L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));
        doThrow(new RuntimeException((String) null)).when(emailService).retryEmail(any(), any());

        scheduler.processEntry(entry, NOW);

        ArgumentCaptor<EmailRetryQueue> captor = ArgumentCaptor.forClass(EmailRetryQueue.class);
        verify(retryQueueRepository).save(captor.capture());
        assertNull(captor.getValue().getUltimoError());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private EmailRetryQueue buildEntry(Long appointmentId, TipoEmail tipo, int intentos) {
        return EmailRetryQueue.builder()
            .id(1L)
            .appointmentId(appointmentId)
            .tipoEmail(tipo)
            .intentos(intentos)
            .proximoIntento(NOW.minusMinutes(5))
            .estado(EstadoRetry.PENDIENTE)
            .build();
    }

    private Appointment buildAppointment(Long id) {
        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        return Appointment.builder()
            .id(id).idExterno("AG-ABCD-000" + id)
            .nombreCliente("Juan Pérez").email("juan@example.cl").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.CONFIRMED)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }
}
