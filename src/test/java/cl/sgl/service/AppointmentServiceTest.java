package cl.sgl.service;

import cl.sgl.dto.AppointmentCalendarDTO;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.dto.ConfirmPaymentRequest;
import cl.sgl.dto.CreateAppointmentRequest;
import cl.sgl.dto.RescheduleRequest;
import cl.sgl.dto.UpdateAppointmentStatusRequest;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.exception.AppointmentConflictException;
import cl.sgl.exception.RescheduleNotAllowedException;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @Mock
    private EmailService emailService;

    @Mock
    private WhatsAppService whatsAppService;

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
        assertEquals(pendingAppointment.getMonto(), result.getMontoConfirmado());
        assertNotNull(result.getFechaPago());
        verify(appointmentRepository).save(any(Appointment.class));
        verify(emailService).sendConfirmationEmail(any(Appointment.class));
        verify(whatsAppService).sendPaymentConfirmedWhatsApp(any(Appointment.class));
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
        when(appointmentRepository.existsByFechaAndHoraAndEstadoIn(
            eq(req.getFecha()), eq(req.getHora()), anyList())).thenReturn(false);
        // saveAndFlush: primer save — asigna el id de BD
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(6L);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            return a;
        });
        // save: segundo save — persiste el idExterno definitivo
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.createAppointment(req);

        assertNotNull(result);
        assertNotNull(result.getIdExterno());
        assertTrue(result.getIdExterno().matches("AG-[A-Z]{4}-\\d{4}"),
            "idExterno debe tener formato AG-XXXX-NNNN, fue: " + result.getIdExterno());
        // El número de secuencia debe coincidir con el ID de BD asignado (6)
        assertTrue(result.getIdExterno().endsWith("-0006"),
            "El sufijo numérico debe ser el ID de BD (0006), fue: " + result.getIdExterno());
        assertEquals("PENDING", result.getEstado());
        assertEquals("Divorcio Contencioso", result.getMateria());
        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
        verify(appointmentRepository).save(any(Appointment.class));
        // El email de confirmación al cliente NO se envía al crear — solo al confirmar el pago
        verify(emailService, never()).sendConfirmationEmail(any(Appointment.class));
        // WhatsApp de confirmación al cliente SÍ se envía al crear (SGL-034)
        verify(whatsAppService).sendConfirmationWhatsApp(any(Appointment.class));
    }

    @Test
    @DisplayName("createAppointment falla con 409 si el slot tiene un agendamiento PENDING o CONFIRMED")
    void testCreateAppointment_SlotOcupado() {
        CreateAppointmentRequest req = buildRequest();

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoIn(
            eq(req.getFecha()), eq(req.getHora()), anyList())).thenReturn(true);

        assertThrows(AppointmentConflictException.class, () ->
            appointmentService.createAppointment(req));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAppointment conflicto incluye fecha y hora en el mensaje")
    void testCreateAppointment_ConflictoMensajeClaro() {
        CreateAppointmentRequest req = buildRequest();

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoIn(
            eq(req.getFecha()), eq(req.getHora()), anyList())).thenReturn(true);

        AppointmentConflictException ex = assertThrows(AppointmentConflictException.class, () ->
            appointmentService.createAppointment(req));

        assertTrue(ex.getMessage().contains(req.getHora().toString()),
            "El mensaje debe incluir la hora conflictiva");
        assertTrue(ex.getMessage().contains(req.getFecha().toString()),
            "El mensaje debe incluir la fecha conflictiva");
        verify(appointmentRepository, never()).saveAndFlush(any());
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

    // ── SGL-080 PAY-POC (Webpay) ─────────────────────────────────

    @Test
    @DisplayName("confirmWebpayPayment cambia estado a CONFIRMED y envía email")
    void testConfirmWebpayPayment_Exitoso() {
        when(appointmentRepository.findByIdExterno("AG-2026-0001"))
            .thenReturn(Optional.of(pendingAppointment));
        when(appointmentRepository.save(any(Appointment.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        appointmentService.confirmWebpayPayment(
            "AG-2026-0001", "AUTH-123456", new BigDecimal("500000"));

        assertEquals(AppointmentStatus.CONFIRMED, pendingAppointment.getEstado());
        assertEquals("AUTH-123456", pendingAppointment.getCodigoTransaccion());
        assertNotNull(pendingAppointment.getFechaPago());
        verify(appointmentRepository).save(any(Appointment.class));
        verify(emailService).sendConfirmationEmail(any(Appointment.class));
        verify(whatsAppService).sendPaymentConfirmedWhatsApp(any(Appointment.class));
    }

    @Test
    @DisplayName("confirmWebpayPayment lanza ResourceNotFoundException si no existe")
    void testConfirmWebpayPayment_NotFound() {
        when(appointmentRepository.findByIdExterno("AG-XXXX-9999"))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            appointmentService.confirmWebpayPayment(
                "AG-XXXX-9999", "AUTH-999", new BigDecimal("500000")));

        verify(emailService, never()).sendConfirmationEmail(any());
        verify(whatsAppService, never()).sendPaymentConfirmedWhatsApp(any());
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

    // ── SGL-019 AG-DESC ───────────────────────────────────────────

    @Test
    @DisplayName("createAppointment persiste descripcion cuando se proporciona")
    void testCreateAppointment_ConDescripcion() {
        CreateAppointmentRequest req = CreateAppointmentRequest.builder()
            .nombreCliente("Ana Martínez López")
            .email("ana@example.cl")
            .telefono("+56912345678")
            .serviceId(1L)
            .fecha(LocalDate.now().plusDays(3))
            .hora(LocalTime.of(10, 0))
            .aceptaTerminos(true)
            .descripcion("Consulta sobre divorcio por mutuo acuerdo")
            .build();

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoIn(
            eq(req.getFecha()), eq(req.getHora()), anyList())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(7L);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            return a;
        });
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.createAppointment(req);

        assertNotNull(result.getDescripcion());
        assertEquals("Consulta sobre divorcio por mutuo acuerdo", result.getDescripcion());
    }

    @Test
    @DisplayName("createAppointment permite descripcion nula (campo opcional)")
    void testCreateAppointment_SinDescripcion() {
        CreateAppointmentRequest req = buildRequest(); // sin descripcion

        when(legalServiceRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoIn(
            eq(req.getFecha()), eq(req.getHora()), anyList())).thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(8L);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            return a;
        });
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.createAppointment(req);

        assertNull(result.getDescripcion());
    }

    @Test
    @DisplayName("mapToDetail incluye descripcion del agendamiento")
    void testMapToDetail_IncluyeDescripcion() {
        Appointment apt = Appointment.builder()
            .id(1L).idExterno("AG-2026-0001")
            .nombreCliente("Juan Pérez").email("juan@example.com").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 5, 15)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .descripcion("Necesito asesoría urgente")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(apt));

        AppointmentDetailDTO dto = appointmentService.getById(1L);

        assertEquals("Necesito asesoría urgente", dto.getDescripcion());
    }

    @Test
    @DisplayName("mapToSummary incluye descripcion del agendamiento")
    void testMapToSummary_IncluyeDescripcion() {
        Appointment apt = Appointment.builder()
            .id(1L).idExterno("AG-2026-0001")
            .nombreCliente("Juan Pérez").email("juan@example.com").telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 5, 15)).hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .descripcion("Nota del cliente")
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        when(appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus.PENDING))
            .thenReturn(List.of(apt));

        AppointmentSummaryDTO dto = appointmentService.listByStatus("pending").get(0);

        assertEquals("Nota del cliente", dto.getDescripcion());
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

    // ── SGL-050 ADM-FILTER ────────────────────────────────────────

    @Test
    @DisplayName("search sin filtros retorna todos los agendamientos")
    void testSearch_SinFiltros_RetornaTodos() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment, confirmedAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.search(null, null, null, null);

        assertEquals(2, result.size());
        verify(appointmentRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("search con estado filtra correctamente")
    void testSearch_ConEstado_FiltradoPorEstado() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.search(null, "PENDING", null, null);

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getEstado());
    }

    @Test
    @DisplayName("search con estado en español es aceptado")
    void testSearch_ConEstadoEspanol_Aceptado() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(confirmedAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.search(null, "CONFIRMADO", null, null);

        assertEquals(1, result.size());
        assertEquals("CONFIRMED", result.get(0).getEstado());
    }

    @Test
    @DisplayName("search con texto busca en nombre, email e idExterno")
    void testSearch_ConTexto_InvocaRepositorio() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.search("juan", null, null, null);

        assertEquals(1, result.size());
        verify(appointmentRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("search con rango de fechas aplica ambos predicados")
    void testSearch_ConRangoFechas_AplicaPredicados() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);

        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result = appointmentService.search(null, null, desde, hasta);

        assertEquals(1, result.size());
        verify(appointmentRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("search combinando todos los filtros invoca repositorio una sola vez")
    void testSearch_TodosFiltrosCombinados_InvocaUnaVez() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        LocalDate hasta = LocalDate.of(2026, 6, 30);

        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment));

        List<AppointmentSummaryDTO> result =
            appointmentService.search("juan", "PENDING", desde, hasta);

        assertEquals(1, result.size());
        verify(appointmentRepository, times(1)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("search con estado inválido lanza IllegalArgumentException")
    void testSearch_EstadoInvalido_LanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
            appointmentService.search(null, "INVALIDO", null, null));

        verify(appointmentRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("search sin resultados retorna lista vacía")
    void testSearch_SinResultados_RetornaListaVacia() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(Collections.emptyList());

        List<AppointmentSummaryDTO> result = appointmentService.search("inexistente", null, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── SGL-051 ADM-EXPORT ────────────────────────────────────────

    @Test
    @DisplayName("exportCsv incluye header como primera línea")
    void testExportCsv_IncluyeHeader() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(Collections.emptyList());

        String csv = appointmentService.exportCsv(null, null, null, null);

        String[] lines = csv.split("\r\n");
        assertEquals("ID,ID Externo,Cliente,Email,Teléfono,Servicio,Fecha,Hora,Monto,Estado,Código Transacción,Descripción,Fecha Creación",
            lines[0]);
    }

    @Test
    @DisplayName("exportCsv sin resultados retorna solo el header")
    void testExportCsv_SinResultados_SoloHeader() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(Collections.emptyList());

        String csv = appointmentService.exportCsv(null, null, null, null);

        String[] lines = csv.split("\r\n");
        assertEquals(1, lines.length);
    }

    @Test
    @DisplayName("exportCsv genera una fila por agendamiento con los campos correctos")
    void testExportCsv_FilaContenidoCorrecto() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(pendingAppointment));

        String csv = appointmentService.exportCsv(null, null, null, null);

        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length); // header + 1 fila
        String row = lines[1];
        assertTrue(row.contains("AG-2026-0001"), "debe incluir idExterno");
        assertTrue(row.contains("Juan Pérez"),   "debe incluir nombreCliente");
        assertTrue(row.contains("juan@example.com"), "debe incluir email");
        assertTrue(row.contains("PENDING"),      "debe incluir estado");
        assertTrue(row.contains("500000"),       "debe incluir monto");
        assertTrue(row.contains("10:00"),        "debe incluir hora formateada");
        assertTrue(row.contains("2026-05-15"),   "debe incluir fecha ISO");
    }

    @Test
    @DisplayName("exportCsv escapa campos que contienen comas")
    void testExportCsv_EscapaCamposConComa() {
        String nombreConComa = "Pérez, Juan Carlos";
        Appointment aptConComa = Appointment.builder()
            .id(5L).idExterno("AG-ZZZZ-0005")
            .nombreCliente(nombreConComa).email("jc@example.cl").telefono("+56911111111")
            .service(servicio)
            .fecha(LocalDate.of(2026, 6, 20)).hora(LocalTime.of(9, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(aptConComa));

        String csv = appointmentService.exportCsv(null, null, null, null);

        assertTrue(csv.contains("\"Pérez, Juan Carlos\""),
            "nombre con coma debe estar entre comillas dobles");
    }

    @Test
    @DisplayName("escapeCsv devuelve string vacío para null")
    void testEscapeCsv_Null() {
        assertEquals("", AppointmentService.escapeCsv(null));
    }

    @Test
    @DisplayName("escapeCsv no altera valores sin caracteres especiales")
    void testEscapeCsv_SinEspeciales() {
        assertEquals("PENDING", AppointmentService.escapeCsv("PENDING"));
    }

    @Test
    @DisplayName("escapeCsv envuelve en comillas y escapa comillas internas")
    void testEscapeCsv_ConComillasInternas() {
        assertEquals("\"dijo \"\"hola\"\"\"", AppointmentService.escapeCsv("dijo \"hola\""));
    }

    @Test
    @DisplayName("exportCsv aplica filtros correctamente (delega a buildSpec)")
    void testExportCsv_AplicaFiltros() {
        when(appointmentRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(confirmedAppointment));

        String csv = appointmentService.exportCsv("maria", "CONFIRMED", null, null);

        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length);
        verify(appointmentRepository, times(1))
            .findAll(any(Specification.class), any(Sort.class));
    }

    // ── SGL-064 GES-REAG-WEB ─────────────────────────────────────────────

    private Appointment buildFutureAppointment(AppointmentStatus estado) {
        return Appointment.builder()
            .id(10L)
            .idExterno("AG-TEST-0010")
            .nombreCliente("Carlos Fuentes")
            .email("carlos@example.com")
            .telefono("+56911111111")
            .service(servicio)
            .fecha(LocalDate.now().plusDays(3))
            .hora(LocalTime.of(14, 0))
            .monto(new BigDecimal("500000"))
            .estado(estado)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("reschedule lanza ResourceNotFoundException si la cita no existe")
    void testReschedule_CitaNoEncontrada_LanzaResourceNotFoundException() {
        when(appointmentRepository.findByIdExterno("AG-XXXX-9999")).thenReturn(Optional.empty());

        RescheduleRequest req = new RescheduleRequest(LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        assertThrows(ResourceNotFoundException.class, () ->
            appointmentService.reschedule("AG-XXXX-9999", req)
        );
    }

    @Test
    @DisplayName("reschedule lanza RescheduleNotAllowedException si la cita está CANCELLED")
    void testReschedule_CitaCancelada_LanzaRescheduleNotAllowedException() {
        Appointment cancelada = buildFutureAppointment(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(cancelada));

        RescheduleRequest req = new RescheduleRequest(LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        RescheduleNotAllowedException ex = assertThrows(RescheduleNotAllowedException.class, () ->
            appointmentService.reschedule("AG-TEST-0010", req)
        );
        assertTrue(ex.getMessage().contains("cancelada"));
    }

    @Test
    @DisplayName("reschedule lanza RescheduleNotAllowedException si la cita es en menos de 24h")
    void testReschedule_MenosDe24Horas_LanzaRescheduleNotAllowedException() {
        Appointment proxima = Appointment.builder()
            .id(10L)
            .idExterno("AG-TEST-0010")
            .nombreCliente("Carlos Fuentes")
            .email("carlos@example.com")
            .telefono("+56911111111")
            .service(servicio)
            .fecha(LocalDate.now())
            .hora(LocalTime.of(8, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .build();
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(proxima));

        RescheduleRequest req = new RescheduleRequest(LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        RescheduleNotAllowedException ex = assertThrows(RescheduleNotAllowedException.class, () ->
            appointmentService.reschedule("AG-TEST-0010", req)
        );
        assertTrue(ex.getMessage().contains("24 horas"));
    }

    @Test
    @DisplayName("reschedule lanza RescheduleNotAllowedException si el nuevo slot está ocupado")
    void testReschedule_SlotOcupado_LanzaRescheduleNotAllowedException() {
        Appointment futura = buildFutureAppointment(AppointmentStatus.PENDING);
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(futura));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoInAndIdNot(any(), any(), anyList(), anyLong()))
            .thenReturn(true);

        RescheduleRequest req = new RescheduleRequest(LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        RescheduleNotAllowedException ex = assertThrows(RescheduleNotAllowedException.class, () ->
            appointmentService.reschedule("AG-TEST-0010", req)
        );
        assertTrue(ex.getMessage().contains("reservado"));
    }

    @Test
    @DisplayName("reschedule con cita CONFIRMED cambia estado a PENDING y actualiza fecha/hora")
    void testReschedule_CitaConfirmada_CambiaEstadoAPending() {
        Appointment futura = buildFutureAppointment(AppointmentStatus.CONFIRMED);
        LocalDate nuevaFecha = LocalDate.now().plusDays(5);
        LocalTime nuevaHora  = LocalTime.of(9, 0);

        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(futura));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoInAndIdNot(any(), any(), anyList(), anyLong()))
            .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.reschedule("AG-TEST-0010",
            new RescheduleRequest(nuevaFecha, nuevaHora));

        assertEquals("PENDING", result.getEstado());
        assertEquals(nuevaFecha, result.getFecha());
        assertEquals(nuevaHora, result.getHora());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("reschedule con cita PENDING cambia estado a RESCHEDULED y actualiza fecha/hora")
    void testReschedule_CitaPendiente_CambiaEstadoARescheduled() {
        Appointment futura = buildFutureAppointment(AppointmentStatus.PENDING);
        LocalDate nuevaFecha = LocalDate.now().plusDays(5);
        LocalTime nuevaHora  = LocalTime.of(11, 0);

        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(futura));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoInAndIdNot(any(), any(), anyList(), anyLong()))
            .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentDetailDTO result = appointmentService.reschedule("AG-TEST-0010",
            new RescheduleRequest(nuevaFecha, nuevaHora));

        assertEquals("RESCHEDULED", result.getEstado());
        assertEquals(nuevaFecha, result.getFecha());
        assertEquals(nuevaHora, result.getHora());
    }

    @Test
    @DisplayName("reschedule excluye la propia cita al verificar disponibilidad del slot")
    void testReschedule_ExcluyeCitaPropiaAlVerificarSlot() {
        Appointment futura = buildFutureAppointment(AppointmentStatus.PENDING);
        LocalDate nuevaFecha = LocalDate.now().plusDays(5);
        LocalTime nuevaHora  = LocalTime.of(14, 0);

        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(futura));
        when(appointmentRepository.existsByFechaAndHoraAndEstadoInAndIdNot(
                eq(nuevaFecha), eq(nuevaHora), anyList(), eq(10L)))
            .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        appointmentService.reschedule("AG-TEST-0010", new RescheduleRequest(nuevaFecha, nuevaHora));

        verify(appointmentRepository).existsByFechaAndHoraAndEstadoInAndIdNot(
            eq(nuevaFecha), eq(nuevaHora), anyList(), eq(10L));
    }
}
