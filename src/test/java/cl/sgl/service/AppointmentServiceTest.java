package cl.sgl.service;

import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AppointmentService.
 *
 * Historia: SGL-045 ADM-LIST-PEND
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService Tests")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private LegalService servicio;
    private Appointment pendingAppointment;
    private Appointment confirmedAppointment;

    @BeforeEach
    void setUp() {
        servicio = LegalService.builder()
            .id(1L)
            .name("Divorcio Contencioso")
            .price(new BigDecimal("500000"))
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        pendingAppointment = Appointment.builder()
            .id(1L)
            .idExterno("AG-2026-0001")
            .nombreCliente("Juan Pérez")
            .email("juan@example.com")
            .telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 5, 15))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        confirmedAppointment = Appointment.builder()
            .id(2L)
            .idExterno("AG-2026-0002")
            .nombreCliente("María González")
            .email("maria@example.com")
            .telefono("+56987654321")
            .service(servicio)
            .fecha(LocalDate.of(2026, 5, 16))
            .hora(LocalTime.of(11, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.CONFIRMED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("listByStatus con PENDING retorna solo agendamientos pendientes")
    void testListByStatus_Pending() {
        when(appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.PENDING))
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.listByStatus("pending");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("AG-2026-0001", result.get(0).getIdExterno());
        assertEquals("Juan Pérez", result.get(0).getNombreCliente());
        assertEquals("Divorcio Contencioso", result.get(0).getMateria());
        assertEquals("PENDING", result.get(0).getEstado());

        verify(appointmentRepository).findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.PENDING);
        verify(appointmentRepository, never()).findAllByOrderByFechaAscHoraAsc();
    }

    @Test
    @DisplayName("listByStatus es insensible a mayúsculas")
    void testListByStatus_CaseInsensitive() {
        when(appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.CONFIRMED))
            .thenReturn(List.of(confirmedAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.listByStatus("CONFIRMED");

        assertEquals(1, result.size());
        assertEquals("CONFIRMED", result.get(0).getEstado());

        verify(appointmentRepository).findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("listByStatus con null retorna todos los agendamientos")
    void testListByStatus_NullRetornaAll() {
        when(appointmentRepository.findAllByOrderByFechaAscHoraAsc())
            .thenReturn(List.of(pendingAppointment, confirmedAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.listByStatus(null);

        assertEquals(2, result.size());

        verify(appointmentRepository).findAllByOrderByFechaAscHoraAsc();
        verify(appointmentRepository, never()).findByEstadoOrderByFechaAscHoraAsc(any());
    }

    @Test
    @DisplayName("listByStatus con string vacío retorna todos los agendamientos")
    void testListByStatus_BlankRetornaAll() {
        when(appointmentRepository.findAllByOrderByFechaAscHoraAsc())
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.listByStatus("   ");

        assertEquals(1, result.size());

        verify(appointmentRepository).findAllByOrderByFechaAscHoraAsc();
    }

    @Test
    @DisplayName("listByStatus con estado inválido lanza IllegalArgumentException")
    void testListByStatus_EstadoInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.listByStatus("INVALIDO")
        );

        verify(appointmentRepository, never()).findByEstadoOrderByFechaAscHoraAsc(any());
        verify(appointmentRepository, never()).findAllByOrderByFechaAscHoraAsc();
    }

    @Test
    @DisplayName("listByStatus retorna lista vacía si no hay agendamientos")
    void testListByStatus_SinResultados() {
        when(appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.CANCELLED))
            .thenReturn(List.of());

        List<AppointmentSummaryDTO> result = appointmentService.listByStatus("CANCELLED");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("mapToSummary incluye todos los campos del DTO")
    void testListByStatus_MapeoCompleto() {
        when(appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.PENDING))
            .thenReturn(List.of(pendingAppointment));

        AppointmentSummaryDTO dto = appointmentService.listByStatus("pending").get(0);

        assertEquals(1L, dto.getId());
        assertEquals("AG-2026-0001", dto.getIdExterno());
        assertEquals("Juan Pérez", dto.getNombreCliente());
        assertEquals("juan@example.com", dto.getEmail());
        assertEquals("Divorcio Contencioso", dto.getMateria());
        assertEquals(LocalDate.of(2026, 5, 15), dto.getFecha());
        assertEquals(LocalTime.of(10, 0), dto.getHora());
        assertEquals(new BigDecimal("500000"), dto.getMonto());
        assertEquals("PENDING", dto.getEstado());
    }
}
