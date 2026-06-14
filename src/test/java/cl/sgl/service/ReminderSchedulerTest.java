package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.entity.ReminderLog;
import cl.sgl.entity.ReminderTipo;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.ReminderLogRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ReminderScheduler.
 *
 * El constructor acepta firstLeadMinutes y secondLeadMinutes directamente,
 * lo que permite probar con valores arbitrarios sin necesitar Spring.
 *
 * Historia: SGL-035 NOTIF-REMIND
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderScheduler Tests")
class ReminderSchedulerTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock ReminderLogRepository  reminderLogRepository;
    @Mock EmailService           emailService;

    ReminderScheduler scheduler;

    private static final LocalDate FECHA = LocalDate.of(2026, 6, 15);
    private static final LocalTime HORA  = LocalTime.of(14, 0);

    @BeforeEach
    void setUp() {
        scheduler = new ReminderScheduler(appointmentRepository, reminderLogRepository, emailService, 1440, 120);
    }

    // ── enviarRecordatorios — REMIND_24H ──────────────────────────────────

    @Test
    @DisplayName("enviarRecordatorios REMIND_24H envía email y guarda log cuando no existe registro previo")
    void testEnviarRecordatorios_24h_EnviaYRegistraSiNoHayLog() {
        Appointment cita = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(cita));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(1L, ReminderTipo.REMIND_24H))
            .thenReturn(false);
        when(emailService.sendReminderEmail(cita, ReminderTipo.REMIND_24H)).thenReturn(true);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_24H);

        verify(emailService).sendReminderEmail(cita, ReminderTipo.REMIND_24H);
        verify(reminderLogRepository).save(any(ReminderLog.class));
    }

    @Test
    @DisplayName("enviarRecordatorios REMIND_24H no guarda log si el envío de email falla")
    void testEnviarRecordatorios_24h_NoGuardaLogSiEmailFalla() {
        Appointment cita = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(cita));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(1L, ReminderTipo.REMIND_24H))
            .thenReturn(false);
        when(emailService.sendReminderEmail(cita, ReminderTipo.REMIND_24H)).thenReturn(false);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_24H);

        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("enviarRecordatorios REMIND_24H omite cita cuando ya existe log")
    void testEnviarRecordatorios_24h_OmiteSiYaExisteLog() {
        Appointment cita = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(cita));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(1L, ReminderTipo.REMIND_24H))
            .thenReturn(true);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_24H);

        verify(emailService, never()).sendReminderEmail(any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("enviarRecordatorios REMIND_24H no falla cuando no hay citas para esa fecha/hora")
    void testEnviarRecordatorios_24h_ListaVacia_NadaOcurre() {
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of());

        assertDoesNotThrow(() -> scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_24H));
        verify(emailService, never()).sendReminderEmail(any(), any());
    }

    @Test
    @DisplayName("enviarRecordatorios REMIND_24H procesa varias citas y omite las que ya tienen log")
    void testEnviarRecordatorios_24h_MultiplesCitas_OmiteLasYaRegistradas() {
        Appointment c1 = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        Appointment c2 = buildAppointment(2L, "AG-2026-0002", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(c1, c2));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(eq(1L), eq(ReminderTipo.REMIND_24H)))
            .thenReturn(false);
        when(reminderLogRepository.existsByAppointmentIdAndTipo(eq(2L), eq(ReminderTipo.REMIND_24H)))
            .thenReturn(true);
        when(emailService.sendReminderEmail(c1, ReminderTipo.REMIND_24H)).thenReturn(true);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_24H);

        verify(emailService).sendReminderEmail(c1, ReminderTipo.REMIND_24H);
        verify(emailService, never()).sendReminderEmail(eq(c2), any());
        verify(reminderLogRepository, times(1)).save(any(ReminderLog.class));
    }

    // ── enviarRecordatorios — REMIND_2H ───────────────────────────────────

    @Test
    @DisplayName("enviarRecordatorios REMIND_2H envía email y guarda log cuando no existe registro previo")
    void testEnviarRecordatorios_2h_EnviaYRegistraSiNoHayLog() {
        Appointment cita = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(cita));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(1L, ReminderTipo.REMIND_2H))
            .thenReturn(false);
        when(emailService.sendReminderEmail(cita, ReminderTipo.REMIND_2H)).thenReturn(true);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_2H);

        verify(emailService).sendReminderEmail(cita, ReminderTipo.REMIND_2H);
        verify(reminderLogRepository).save(any(ReminderLog.class));
    }

    @Test
    @DisplayName("enviarRecordatorios REMIND_2H omite cita cuando ya existe log")
    void testEnviarRecordatorios_2h_OmiteSiYaExisteLog() {
        Appointment cita = buildAppointment(1L, "AG-2026-0001", FECHA, HORA);
        when(appointmentRepository.findByEstadoAndFechaAndHora(AppointmentStatus.CONFIRMED, FECHA, HORA))
            .thenReturn(List.of(cita));
        when(reminderLogRepository.existsByAppointmentIdAndTipo(1L, ReminderTipo.REMIND_2H))
            .thenReturn(true);

        scheduler.enviarRecordatorios(LocalDateTime.of(FECHA, HORA), ReminderTipo.REMIND_2H);

        verify(emailService, never()).sendReminderEmail(any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    // ── helper ────────────────────────────────────────────────────────────

    private Appointment buildAppointment(Long id, String idExterno, LocalDate fecha, LocalTime hora) {
        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        return Appointment.builder()
            .id(id).idExterno(idExterno)
            .nombreCliente("Juan Pérez").email("juan@example.cl").telefono("+56912345678")
            .service(servicio).fecha(fecha).hora(hora)
            .monto(new BigDecimal("500000")).estado(AppointmentStatus.CONFIRMED)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }
}
