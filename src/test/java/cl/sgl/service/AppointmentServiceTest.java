package cl.sgl.service;

import cl.sgl.dto.AppointmentCalendarDTO;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.dto.ConfirmPaymentRequest;
import cl.sgl.dto.CreateAppointmentRequest;
import cl.sgl.dto.UpdateAppointmentStatusRequest;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.LegalServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para AppointmentService.
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-046 ADM-DETAIL
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService Tests")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private LegalServiceRepository legalServiceRepository;

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

    // ── SGL-046 ADM-DETAIL ────────────────────────────────────────────

    @Test
    @DisplayName("getById retorna detalle completo cuando existe")
    void testGetById_Success() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingAppointment));

        AppointmentDetailDTO dto = appointmentService.getById(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("AG-2026-0001", dto.getIdExterno());
        assertEquals("Juan Pérez", dto.getNombreCliente());
        assertEquals("juan@example.com", dto.getEmail());
        assertEquals("+56912345678", dto.getTelefono());
        assertEquals(1L, dto.getServicioId());
        assertEquals("Divorcio Contencioso", dto.getMateria());
        assertEquals(LocalDate.of(2026, 5, 15), dto.getFecha());
        assertEquals(LocalTime.of(10, 0), dto.getHora());
        assertEquals(new BigDecimal("500000"), dto.getMonto());
        assertEquals("PENDING", dto.getEstado());
        assertNotNull(dto.getCreatedAt());
        assertNotNull(dto.getUpdatedAt());

        verify(appointmentRepository).findById(1L);
    }

    @Test
    @DisplayName("getById lanza ResourceNotFoundException cuando no existe")
    void testGetById_NotFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.getById(999L));

        verify(appointmentRepository).findById(999L);
    }

    // ── SGL-048 PAY-MANUAL-CONF ──────────────────────────────────

    @Test
    @DisplayName("confirmPayment registra transacción, toma el monto del agendamiento y cambia estado a CONFIRMED")
    void testConfirmPayment_Success() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.confirmPayment(1L,
            new ConfirmPaymentRequest("TXN-12345678"));

        assertEquals("CONFIRMED",    result.getEstado());
        assertEquals("TXN-12345678", result.getCodigoTransaccion());
        // montoConfirmado debe ser igual al monto del agendamiento
        assertEquals(pendingAppointment.getMonto(), result.getMontoConfirmado());
        assertNotNull(result.getFechaPago());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("confirmPayment lanza ResourceNotFoundException si el agendamiento no existe")
    void testConfirmPayment_NotFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            appointmentService.confirmPayment(999L, new ConfirmPaymentRequest("TXN-99999")));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmPayment lanza IllegalArgumentException si el agendamiento está cancelado")
    void testConfirmPayment_Cancelado() {
        Appointment cancelado = Appointment.builder()
            .id(3L).idExterno("AG-2026-0003")
            .nombreCliente("Test").email("t@t.cl").telefono("+56900000000")
            .service(servicio)
            .fecha(LocalDate.now().plusDays(5)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("85000"))
            .estado(AppointmentStatus.CANCELLED)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(cancelado));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.confirmPayment(3L, new ConfirmPaymentRequest("TXN-99999")));

        verify(appointmentRepository, never()).save(any());
    }

    // ── SGL-047 ADM-STATE ────────────────────────────────────────

    @Test
    @DisplayName("updateStatus cambia el estado correctamente y retorna DTO actualizado")
    void testUpdateStatus_Success() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.updateStatus(1L,
            new UpdateAppointmentStatusRequest("CONFIRMADO"));

        assertNotNull(result);
        assertEquals("CONFIRMED", result.getEstado());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("updateStatus acepta valores en inglés")
    void testUpdateStatus_InglesAceptado() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.updateStatus(1L,
            new UpdateAppointmentStatusRequest("CANCELLED"));

        assertEquals("CANCELLED", result.getEstado());
    }

    @Test
    @DisplayName("updateStatus lanza IllegalArgumentException con estado inválido")
    void testUpdateStatus_EstadoInvalido() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingAppointment));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.updateStatus(1L, new UpdateAppointmentStatusRequest("INVALIDO")));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus lanza ResourceNotFoundException si el agendamiento no existe")
    void testUpdateStatus_NotFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            appointmentService.updateStatus(999L, new UpdateAppointmentStatusRequest("CONFIRMADO")));

        verify(appointmentRepository, never()).save(any());
    }

    // ── SGL-021 AG-HORAS ─────────────────────────────────────────

    @Test
    @DisplayName("getAvailableHours retorna 9 slots cuando no hay agendamientos")
    void testGetAvailableHours_TodosLibres() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        when(appointmentRepository.findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED))
            .thenReturn(Collections.emptyList());

        List<String> result = appointmentService.getAvailableHours(fecha);

        assertEquals(9, result.size());
        assertEquals("09:00", result.get(0));
        assertEquals("17:00", result.get(8));
        verify(appointmentRepository).findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("getAvailableHours excluye slots ocupados por agendamientos activos")
    void testGetAvailableHours_ExcluyeOcupados() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        when(appointmentRepository.findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED))
            .thenReturn(List.of(LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(14, 0)));

        List<String> result = appointmentService.getAvailableHours(fecha);

        assertEquals(6, result.size());
        assertFalse(result.contains("09:00"));
        assertFalse(result.contains("11:00"));
        assertFalse(result.contains("14:00"));
        assertTrue(result.contains("10:00"));
        assertTrue(result.contains("12:00"));
    }

    @Test
    @DisplayName("getAvailableHours retorna lista vacía si todos los slots están ocupados")
    void testGetAvailableHours_TodosOcupados() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        List<LocalTime> todosOcupados = List.of(
            LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(14, 0),
            LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0)
        );
        when(appointmentRepository.findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED))
            .thenReturn(todosOcupados);

        List<String> result = appointmentService.getAvailableHours(fecha);

        assertTrue(result.isEmpty());
    }

    // ── SGL-024 AG-IDEXTERNO ──────────────────────────────────────

    private CreateAppointmentRequest buildRequest() {
        return CreateAppointmentRequest.builder()
            .nombreCliente("Ana Martínez López")
            .email("ana@example.cl")
            .telefono("+56912345678")
            .serviceId(1L)
            .fecha(LocalDate.now().plusDays(3))
            .hora(LocalTime.of(10, 0))
            .aceptaTerminos(true)
            .build();
    }

    @Test
    @DisplayName("createAppointment crea agendamiento y retorna idExterno en formato AG-XXXX-NNNN")
    void testCreateAppointment_Success() {
        CreateAppointmentRequest req = buildRequest();

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoNot(
            req.getFecha(), req.getHora(), AppointmentStatus.CANCELLED)).thenReturn(false);
        when(appointmentRepository.count()).thenReturn(5L);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(6L);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            return a;
        });

        AppointmentDetailDTO result = appointmentService.createAppointment(req);

        assertNotNull(result);
        assertNotNull(result.getIdExterno());
        assertTrue(result.getIdExterno().matches("AG-[A-Z]{4}-\\d{4}"),
            "idExterno debe tener formato AG-XXXX-NNNN, fue: " + result.getIdExterno());
        assertEquals("PENDING", result.getEstado());
        assertEquals("Divorcio Contencioso", result.getMateria());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("createAppointment falla si el slot ya está ocupado")
    void testCreateAppointment_SlotOcupado() {
        CreateAppointmentRequest req = buildRequest();

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoNot(
            req.getFecha(), req.getHora(), AppointmentStatus.CANCELLED)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.createAppointment(req));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAppointment falla si el servicio no existe")
    void testCreateAppointment_ServicioNoEncontrado() {
        CreateAppointmentRequest req = buildRequest();
        when(legalServiceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            appointmentService.createAppointment(req));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAppointment falla si el servicio está inactivo")
    void testCreateAppointment_ServicioInactivo() {
        LegalService inactivo = LegalService.builder()
            .id(1L).name("Servicio Inactivo").price(new BigDecimal("100000"))
            .active(false).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
        CreateAppointmentRequest req = buildRequest();
        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(inactivo));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.createAppointment(req));

        verify(appointmentRepository, never()).save(any());
    }

    // ── SGL-020 AG-FECHAS ─────────────────────────────────────────

    @Test
    @DisplayName("getAvailableDays retorna solo lunes a viernes en la ventana")
    void testGetAvailableDays_ExcluyeFinDeSemana() {
        // 2026-05-11 es lunes
        LocalDate lunes = LocalDate.of(2026, 5, 11);

        List<String> result = appointmentService.getAvailableDays(lunes, 7);

        // 7 días calendario desde lunes → lun/mar/mié/jue/vie/sáb/dom → 5 hábiles
        assertEquals(5, result.size());
        assertEquals("2026-05-11", result.get(0)); // lunes
        assertEquals("2026-05-15", result.get(4)); // viernes
        assertFalse(result.contains("2026-05-16")); // sábado excluido
        assertFalse(result.contains("2026-05-17")); // domingo excluido
    }

    @Test
    @DisplayName("getAvailableDays desde sábado salta al lunes siguiente")
    void testGetAvailableDays_DesdeFinDeSemana() {
        // 2026-05-09 es sábado
        LocalDate sabado = LocalDate.of(2026, 5, 9);

        List<String> result = appointmentService.getAvailableDays(sabado, 3);

        // 3 días: sáb/dom/lun → solo lunes hábil
        assertEquals(1, result.size());
        assertEquals("2026-05-11", result.get(0));
    }

    @Test
    @DisplayName("getAvailableDays con days=30 retorna aprox 21-22 días hábiles")
    void testGetAvailableDays_VentanaMes() {
        LocalDate lunes = LocalDate.of(2026, 5, 11);

        List<String> result = appointmentService.getAvailableDays(lunes, 30);

        // 30 días calendario = 4 semanas + 2 → entre 20 y 22 hábiles
        assertTrue(result.size() >= 20 && result.size() <= 22);
        result.forEach(d -> {
            DayOfWeek dow = LocalDate.parse(d).getDayOfWeek();
            assertNotEquals(DayOfWeek.SATURDAY, dow);
            assertNotEquals(DayOfWeek.SUNDAY, dow);
        });
    }

    @Test
    @DisplayName("getAvailableDays con days=1 retorna máximo 1 día hábil")
    void testGetAvailableDays_UnDia() {
        LocalDate lunes = LocalDate.of(2026, 5, 11);
        List<String> result = appointmentService.getAvailableDays(lunes, 1);
        assertEquals(1, result.size());
        assertEquals("2026-05-11", result.get(0));
    }

    @Test
    @DisplayName("getAvailableDays retorna lista vacía si la ventana cae en fin de semana completo")
    void testGetAvailableDays_VentanaFinDeSemana() {
        // 2026-05-09 sábado, ventana 2 días → sáb + dom, 0 hábiles
        LocalDate sabado = LocalDate.of(2026, 5, 9);
        List<String> result = appointmentService.getAvailableDays(sabado, 2);
        assertTrue(result.isEmpty());
    }

    // ── SGL-049 ADM-CAL ───────────────────────────────────────────

    @Test
    @DisplayName("getCalendario retorna metadatos y agendamientos agrupados por fecha")
    void testGetCalendario_Exitoso() {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 7);
        when(appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta))
            .thenReturn(List.of(pendingAppointment)); // fecha 2026-05-15 cae fuera, pero mockito devuelve lo que se le dice

        AppointmentCalendarDTO result = appointmentService.getCalendario("2026-05", 1);

        assertNotNull(result);
        assertEquals("2026-05", result.getMes());
        assertEquals(1, result.getSemana());
        assertEquals(5, result.getTotalSemanas()); // ceil(31/7) = 5
        assertEquals("2026-05-01", result.getDesde());
        assertEquals("2026-05-07", result.getHasta());
        assertTrue(result.isPrimera());
        assertFalse(result.isUltima());
        assertNotNull(result.getDias());
        verify(appointmentRepository).findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta);
    }

    @Test
    @DisplayName("getCalendario agrupa correctamente múltiples agendamientos en la misma fecha")
    void testGetCalendario_AgrupaAgendamientosPorFecha() {
        Appointment segundaCita = Appointment.builder()
            .id(3L).idExterno("AG-ZZZZ-0003")
            .nombreCliente("Pedro Soto").email("pedro@example.cl").telefono("+56911111111")
            .service(servicio)
            .fecha(LocalDate.of(2026, 5, 15)) // misma fecha que pendingAppointment
            .hora(LocalTime.of(11, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        LocalDate desde = LocalDate.of(2026, 5, 15);
        LocalDate hasta = LocalDate.of(2026, 5, 21);
        when(appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta))
            .thenReturn(List.of(pendingAppointment, segundaCita));

        AppointmentCalendarDTO result = appointmentService.getCalendario("2026-05", 3);

        assertEquals(1, result.getDias().size()); // una sola clave de fecha
        List<AppointmentSummaryDTO> citasDelDia = result.getDias().get("2026-05-15");
        assertNotNull(citasDelDia);
        assertEquals(2, citasDelDia.size());
        assertEquals(LocalTime.of(10, 0), citasDelDia.get(0).getHora());
        assertEquals(LocalTime.of(11, 0), citasDelDia.get(1).getHora());
    }

    @Test
    @DisplayName("getCalendario retorna mapa vacío si no hay agendamientos en la semana")
    void testGetCalendario_SinAgendamientos() {
        LocalDate desde = LocalDate.of(2026, 5, 1);
        LocalDate hasta = LocalDate.of(2026, 5, 7);
        when(appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta))
            .thenReturn(List.of());

        AppointmentCalendarDTO result = appointmentService.getCalendario("2026-05", 1);

        assertNotNull(result.getDias());
        assertTrue(result.getDias().isEmpty());
    }

    @Test
    @DisplayName("getCalendario recorta 'hasta' al último día del mes en la semana final")
    void testGetCalendario_UltimaSemanaRecortadaAlFinDeMes() {
        // Mayo 2026 tiene 31 días. Semana 5: días 29–31 (no 29–35).
        LocalDate desde = LocalDate.of(2026, 5, 29);
        LocalDate hasta = LocalDate.of(2026, 5, 31);
        when(appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta))
            .thenReturn(List.of());

        AppointmentCalendarDTO result = appointmentService.getCalendario("2026-05", 5);

        assertEquals("2026-05-29", result.getDesde());
        assertEquals("2026-05-31", result.getHasta());
        assertTrue(result.isUltima());
        assertFalse(result.isPrimera());
        verify(appointmentRepository).findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta);
    }

    @Test
    @DisplayName("getCalendario con mes en formato inválido lanza IllegalArgumentException")
    void testGetCalendario_MesInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.getCalendario("05-2026", 1));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.getCalendario("no-es-fecha", 1));

        verify(appointmentRepository, never()).findByFechaBetweenOrderByFechaAscHoraAsc(any(), any());
    }

    @Test
    @DisplayName("getCalendario con semana fuera de rango lanza IllegalArgumentException")
    void testGetCalendario_SemanaFueraDeRango() {
        // Mayo 2026: totalSemanas = 5
        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.getCalendario("2026-05", 0));

        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.getCalendario("2026-05", 6));

        verify(appointmentRepository, never()).findByFechaBetweenOrderByFechaAscHoraAsc(any(), any());
    }

    @Test
    @DisplayName("getCalendario en mes de 28 días tiene exactamente 4 semanas")
    void testGetCalendario_MesConCuatroSemanas() {
        // Febrero 2026: 28 días → ceil(28/7) = 4 semanas exactas
        LocalDate desde = LocalDate.of(2026, 2, 22);
        LocalDate hasta = LocalDate.of(2026, 2, 28);
        when(appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta))
            .thenReturn(List.of());

        AppointmentCalendarDTO result = appointmentService.getCalendario("2026-02", 4);

        assertEquals(4, result.getTotalSemanas());
        assertEquals("2026-02-22", result.getDesde());
        assertEquals("2026-02-28", result.getHasta());
        assertTrue(result.isUltima());
    }

    @Test
    @DisplayName("getAvailableHours para hoy retorna slots disponibles")
    void testGetAvailableHours_Hoy() {
        LocalDate hoy = LocalDate.now();
        when(appointmentRepository.findBookedHoursForDate(hoy, AppointmentStatus.CANCELLED))
            .thenReturn(Collections.emptyList());

        List<String> result = appointmentService.getAvailableHours(hoy);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getAvailableHours no excluye slots de agendamientos CANCELLED")
    void testGetAvailableHours_CancelledNoBloquea() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        // findBookedHoursForDate excluye CANCELLED internamente en el repo
        // → si la query solo devuelve PENDING/CONFIRMED/RESCHEDULED, los CANCELLED no aparecen
        when(appointmentRepository.findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED))
            .thenReturn(Collections.emptyList()); // CANCELLED no bloquea → lista vacía

        List<String> result = appointmentService.getAvailableHours(fecha);

        assertEquals(9, result.size()); // todos libres
    }

    @Test
    @DisplayName("getById incluye descripción del servicio")
    void testGetById_IncluyeDescripcionServicio() {
        servicio = LegalService.builder()
            .id(1L)
            .name("Divorcio Contencioso")
            .description("Descripción detallada del servicio")
            .price(new BigDecimal("500000"))
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        Appointment apt = Appointment.builder()
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

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        AppointmentDetailDTO dto = appointmentService.getById(1L);

        assertEquals("Descripción detallada del servicio", dto.getDescripcionServicio());
    }
}
