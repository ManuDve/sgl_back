package cl.sgl.service;

import cl.sgl.config.InputSanitizer;
import cl.sgl.dto.AppointmentCalendarDTO;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.dto.ConfirmPaymentRequest;
import cl.sgl.dto.CreateAppointmentRequest;
import cl.sgl.dto.UpdateAppointmentStatusRequest;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.exception.AppointmentConflictException;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.AppointmentSpecification;
import cl.sgl.repository.LegalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

/**
 * Servicio de lógica de negocio para agendamientos.
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-046 ADM-DETAIL, SGL-021 AG-HORAS, SGL-020 AG-FECHAS, SGL-024 AG-IDEXTERNO
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final LegalServiceRepository legalServiceRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    // ── CSV helpers (SGL-051) ─────────────────────────────────────────────

    private static final String CSV_HEADER =
        "ID,ID Externo,Cliente,Email,Teléfono,Servicio,Fecha,Hora,Monto,Estado,Código Transacción,Descripción,Fecha Creación";
    private static final DateTimeFormatter HORA_FMT     = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Escapa un campo CSV: entre comillas dobles si contiene coma, comilla o salto de línea. */
    static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── Specification compartida por search y exportCsv ───────────────────

    private Specification<Appointment> buildSpec(String searchText, String status,
                                                  LocalDate desde, LocalDate hasta) {
        Specification<Appointment> spec = Specification.where(null);

        if (status != null && !status.isBlank()) {
            AppointmentStatus estado = AppointmentStatus.fromString(status);
            spec = spec.and(AppointmentSpecification.hasEstado(estado));
        }
        if (searchText != null && !searchText.isBlank()) {
            spec = spec.and(AppointmentSpecification.searchText(searchText.trim()));
        }
        if (desde != null) {
            spec = spec.and(AppointmentSpecification.fechaDesde(desde));
        }
        if (hasta != null) {
            spec = spec.and(AppointmentSpecification.fechaHasta(hasta));
        }
        return spec;
    }

    /**
     * Busca agendamientos combinando filtros opcionales: texto libre, estado, rango de fechas.
     * Cualquier parámetro puede ser null → se omite ese predicado.
     * Mantiene compatibilidad con el endpoint existente (todos los params null = devuelve todo).
     *
     * @param searchText texto a buscar en nombreCliente, email e idExterno (case-insensitive)
     * @param status     nombre de estado (inglés o español); null = sin filtro de estado
     * @param desde      fecha mínima inclusiva; null = sin límite inferior
     * @param hasta      fecha máxima inclusiva; null = sin límite superior
     * @return lista ordenada por fecha y hora ascendente
     * @throws IllegalArgumentException si el valor de status no es un estado válido
     *
     * Historia: SGL-050 ADM-FILTER
     */
    @Transactional(readOnly = true)
    public List<AppointmentSummaryDTO> search(String searchText, String status,
                                              LocalDate desde, LocalDate hasta) {
        Sort sort = Sort.by(Sort.Direction.ASC, "fecha", "hora");
        List<Appointment> results = appointmentRepository.findAll(buildSpec(searchText, status, desde, hasta), sort);

        log.debug("search(text={}, status={}, desde={}, hasta={}) → {} resultados",
            searchText, status, desde, hasta, results.size());

        return results.stream()
            .map(this::mapToSummary)
            .collect(Collectors.toList());
    }

    /**
     * Exporta agendamientos con los mismos filtros que {@link #search} en formato CSV.
     * Columnas: ID, idExterno, cliente, email, teléfono, servicio, fecha, hora,
     * monto, estado, codigoTransaccion, fechaCreacion.
     *
     * @return contenido CSV como String (UTF-8, separador CRLF, campos con coma escapados)
     * @throws IllegalArgumentException si el valor de status no es un estado válido
     *
     * Historia: SGL-051 ADM-EXPORT
     */
    @Transactional(readOnly = true)
    public String exportCsv(String searchText, String status, LocalDate desde, LocalDate hasta) {
        Sort sort = Sort.by(Sort.Direction.ASC, "fecha", "hora");
        List<Appointment> results = appointmentRepository.findAll(buildSpec(searchText, status, desde, hasta), sort);

        log.info("exportCsv(text={}, status={}, desde={}, hasta={}) → {} filas",
            searchText, status, desde, hasta, results.size());

        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\r\n");

        for (Appointment a : results) {
            sb.append(a.getId()).append(",");
            sb.append(escapeCsv(a.getIdExterno())).append(",");
            sb.append(escapeCsv(a.getNombreCliente())).append(",");
            sb.append(escapeCsv(a.getEmail())).append(",");
            sb.append(escapeCsv(a.getTelefono())).append(",");
            sb.append(escapeCsv(a.getService().getName())).append(",");
            sb.append(a.getFecha().format(ISO_LOCAL_DATE)).append(",");
            sb.append(a.getHora().format(HORA_FMT)).append(",");
            sb.append(a.getMonto().toPlainString()).append(",");
            sb.append(escapeCsv(a.getEstado().name())).append(",");
            sb.append(escapeCsv(a.getCodigoTransaccion())).append(",");
            sb.append(escapeCsv(a.getDescripcion())).append(",");
            sb.append(escapeCsv(a.getCreatedAt() != null
                ? a.getCreatedAt().format(DATETIME_FMT) : ""));
            sb.append("\r\n");
        }

        return sb.toString();
    }

    /**
     * Lista agendamientos filtrados por estado.
     * Si status es null o vacío, devuelve todos.
     *
     * @param status nombre del estado (PENDING, CONFIRMED, CANCELLED, RESCHEDULED) — insensible a mayúsculas
     * @return lista de DTOs resumen ordenada por fecha y hora ascendente
     * @throws IllegalArgumentException si el valor de status no corresponde a un estado válido
     */
    @Transactional(readOnly = true)
    public List<AppointmentSummaryDTO> listByStatus(String status) {
        List<Appointment> appointments;

        if (status == null || status.isBlank()) {
            appointments = appointmentRepository.findAllByOrderByFechaAscHoraAsc();
            log.debug("Listando todos los agendamientos: {} registros", appointments.size());
        } else {
            AppointmentStatus appointmentStatus = AppointmentStatus.fromString(status);
            appointments = appointmentRepository.findByEstadoOrderByFechaAscHoraAsc(appointmentStatus);
            log.debug("Listando agendamientos con estado={}: {} registros", appointmentStatus, appointments.size());
        }

        return appointments.stream()
            .map(this::mapToSummary)
            .collect(Collectors.toList());
    }

    /**
     * Retorna el detalle completo de un agendamiento por ID interno.
     *
     * @param id ID interno del agendamiento
     * @return DTO con todos los campos
     * @throws ResourceNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public AppointmentDetailDTO getById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Agendamiento no encontrado con ID: {}", id);
                return new ResourceNotFoundException("Agendamiento con ID " + id + " no encontrado");
            });

        log.debug("Detalle de agendamiento ID={}", id);
        return mapToDetail(appointment);
    }

    /**
     * Retorna el detalle de un agendamiento por su idExterno (uso público — pantalla de confirmación).
     *
     * @param idExterno identificador externo en formato AG-XXXX-NNNN
     * @return DTO con todos los campos
     * @throws ResourceNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public AppointmentDetailDTO getByIdExterno(String idExterno) {
        Appointment appointment = appointmentRepository.findByIdExterno(idExterno)
            .orElseThrow(() -> {
                log.warn("Agendamiento no encontrado con idExterno: {}", idExterno);
                return new ResourceNotFoundException("Agendamiento '" + idExterno + "' no encontrado");
            });

        log.debug("Detalle público de agendamiento idExterno={}", idExterno);
        return mapToDetail(appointment);
    }

    private AppointmentDetailDTO mapToDetail(Appointment appointment) {
        return AppointmentDetailDTO.builder()
            .id(appointment.getId())
            .idExterno(appointment.getIdExterno())
            .nombreCliente(appointment.getNombreCliente())
            .email(appointment.getEmail())
            .telefono(appointment.getTelefono())
            .servicioId(appointment.getService().getId())
            .materia(appointment.getService().getName())
            .descripcionServicio(appointment.getService().getDescription())
            .descripcion(appointment.getDescripcion())
            .fecha(appointment.getFecha())
            .hora(appointment.getHora())
            .monto(appointment.getMonto())
            .estado(appointment.getEstado().name())
            .codigoTransaccion(appointment.getCodigoTransaccion())
            .montoConfirmado(appointment.getMontoConfirmado())
            .fechaPago(appointment.getFechaPago())
            .createdAt(appointment.getCreatedAt())
            .updatedAt(appointment.getUpdatedAt())
            .build();
    }

    /**
     * Retorna los slots horarios disponibles para una fecha dada.
     * Horario base: 09:00–17:00, intervalo de 60 minutos (America/Santiago).
     * Excluye los slots ocupados por agendamientos activos (no CANCELLED).
     *
     * @param fecha fecha a consultar
     * @return lista de horas disponibles en formato "HH:mm", ordenada
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableHours(LocalDate fecha) {
        List<LocalTime> allSlots = buildSlots();
        Set<LocalTime> booked   = Set.copyOf(
            appointmentRepository.findBookedHoursForDate(fecha, AppointmentStatus.CANCELLED)
        );

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<String> available = allSlots.stream()
            .filter(slot -> !booked.contains(slot))
            .map(fmt::format)
            .collect(Collectors.toList());

        log.debug("Horas disponibles para {}: {}/{} slots libres", fecha, available.size(), allSlots.size());
        return available;
    }

    /**
     * Crea un nuevo agendamiento, genera su idExterno y lo persiste con estado PENDING.
     *
     * @param request datos del agendamiento validados por Bean Validation
     * @return DTO con el agendamiento completo, incluyendo idExterno generado
     * @throws ResourceNotFoundException si el servicio no existe
     * @throws IllegalArgumentException  si el servicio está inactivo o el slot ya está ocupado
     */
    @Transactional
    public AppointmentDetailDTO createAppointment(CreateAppointmentRequest request) {
        LegalService servicio = legalServiceRepository.findById(request.getServiceId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Servicio con ID " + request.getServiceId() + " no encontrado"));

        if (!Boolean.TRUE.equals(servicio.getActive())) {
            throw new IllegalArgumentException(
                "El servicio '" + servicio.getName() + "' no está disponible actualmente.");
        }

        if (appointmentRepository.existsByFechaAndHoraAndEstadoIn(
                request.getFecha(), request.getHora(),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED))) {
            throw new AppointmentConflictException(
                "El horario " + request.getHora() + " del " + request.getFecha()
                + " ya está reservado por otro agendamiento.");
        }

        // Primer save con un placeholder único para obtener el ID de BD asignado por IDENTITY.
        // El idExterno definitivo se genera en el segundo save usando ese ID como secuencia,
        // garantizando que sea monotónico y nunca se repita aunque haya deletes o tests.
        String tempId = "T-" + UUID.randomUUID().toString().substring(0, 13); // único, cabe en length=20

        String descripcionSanitizada = (request.getDescripcion() != null && !request.getDescripcion().isBlank())
            ? InputSanitizer.sanitize(request.getDescripcion().trim())
            : null;

        Appointment appointment = Appointment.builder()
            .idExterno(tempId)
            .nombreCliente(InputSanitizer.sanitize(request.getNombreCliente().trim()))
            .email(request.getEmail().trim().toLowerCase())
            .telefono(request.getTelefono().replaceAll("\\s+", ""))
            .service(servicio)
            .fecha(request.getFecha())
            .hora(request.getHora())
            .monto(servicio.getPrice())
            .estado(AppointmentStatus.PENDING)
            .aceptaTerminos(request.getAceptaTerminos())
            .descripcion(descripcionSanitizada)
            .build();

        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        saved.setIdExterno(generateIdExterno(saved.getId()));
        saved = appointmentRepository.save(saved);

        log.info("Agendamiento creado: {} | {} {} | {}", saved.getIdExterno(),
            saved.getFecha(), saved.getHora(), saved.getNombreCliente());

        // Email de confirmación al cliente se envía solo cuando el pago es aprobado.
        // Notificación al admin se envía inmediatamente para que pueda esperar la transferencia.
        emailService.sendAdminNewAppointmentEmail(saved); // SGL-036
        whatsAppService.sendConfirmationWhatsApp(saved);  // SGL-034 — fallo no bloquea el flujo

        return mapToDetail(saved);
    }

    /**
     * Confirma el pago de un agendamiento vía Transbank WebpayPlus.
     * Registra el código de autorización del banco y cambia el estado a CONFIRMED.
     *
     * @param idExterno         identificador externo del agendamiento (buyOrder en Transbank)
     * @param authorizationCode código de autorización retornado por Transbank
     * @param monto             monto confirmado por Transbank
     * @throws ResourceNotFoundException si no existe el agendamiento
     * @throws IllegalArgumentException  si el agendamiento está CANCELLED
     *
     * Historia: SGL-080 PAY-POC
     */
    @Transactional
    public void confirmWebpayPayment(String idExterno, String authorizationCode, BigDecimal monto) {
        Appointment appointment = appointmentRepository.findByIdExterno(idExterno)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Agendamiento '" + idExterno + "' no encontrado"));

        if (AppointmentStatus.CANCELLED.equals(appointment.getEstado())) {
            throw new IllegalArgumentException(
                "No se puede confirmar el pago de un agendamiento cancelado.");
        }

        appointment.setCodigoTransaccion(authorizationCode);
        appointment.setMontoConfirmado(monto);
        appointment.setFechaPago(LocalDateTime.now());
        appointment.setEstado(AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Pago Webpay confirmado — idExterno: {}, auth: {}", idExterno, authorizationCode);
        emailService.sendConfirmationEmail(saved);
    }

    /**
     * Confirma el pago manual de un agendamiento.
     * Registra número de transacción, monto confirmado y fecha de pago,
     * y cambia el estado a CONFIRMED automáticamente.
     *
     * @param id      ID interno del agendamiento
     * @param request datos del pago (número de transacción y monto)
     * @return DTO actualizado con los campos de pago
     * @throws ResourceNotFoundException si no existe el agendamiento
     * @throws IllegalArgumentException  si el agendamiento está CANCELLED
     */
    @Transactional
    public AppointmentDetailDTO confirmPayment(Long id, ConfirmPaymentRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Agendamiento no encontrado para confirmar pago, ID: {}", id);
                return new ResourceNotFoundException("Agendamiento con ID " + id + " no encontrado");
            });

        if (AppointmentStatus.CANCELLED.equals(appointment.getEstado())) {
            throw new IllegalArgumentException(
                "No se puede confirmar el pago de un agendamiento cancelado.");
        }

        // El monto confirmado se toma del agendamiento (no del request) para evitar errores de digitación.
        // TODO: cuando se integre una pasarela de pago (Transbank/Stripe), este método
        //       podrá ser reemplazado por confirmación automática vía webhook.
        appointment.setCodigoTransaccion(request.getCodigoTransaccion().trim());
        appointment.setMontoConfirmado(appointment.getMonto());
        appointment.setFechaPago(LocalDateTime.now());
        appointment.setEstado(AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Pago confirmado — ID={} | codigo={} | monto={}",
            id, request.getCodigoTransaccion(), appointment.getMonto());
        emailService.sendConfirmationEmail(saved);

        return mapToDetail(saved);
    }

    /**
     * Cambia el estado de un agendamiento existente.
     *
     * @param id      ID interno del agendamiento
     * @param request contiene el nuevo estado (español o inglés)
     * @return DTO actualizado con todos los campos
     * @throws ResourceNotFoundException si no existe el agendamiento
     * @throws IllegalArgumentException  si el valor de estado es inválido
     */
    @Transactional
    public AppointmentDetailDTO updateStatus(Long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Agendamiento no encontrado para cambiar estado, ID: {}", id);
                return new ResourceNotFoundException("Agendamiento con ID " + id + " no encontrado");
            });

        AppointmentStatus nuevoEstado = AppointmentStatus.fromString(request.getEstado());
        AppointmentStatus estadoAnterior = appointment.getEstado();

        appointment.setEstado(nuevoEstado);
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Estado agendamiento ID={} cambiado: {} → {}", id, estadoAnterior, nuevoEstado);
        return mapToDetail(saved);
    }

    /** Genera un idExterno en formato AG-XXXX-NNNN usando el ID de BD como secuencia. */
    private String generateIdExterno(Long dbId) {
        StringBuilder letras = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            letras.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return String.format("AG-%s-%04d", letras, dbId);
    }

    /**
     * Retorna los días hábiles (lunes a viernes) dentro de una ventana de días calendario.
     * No consulta la BD — la disponibilidad de horas se verifica con getAvailableHours.
     *
     * @param from  primer día a considerar (debe ser hoy o futuro)
     * @param days  ventana en días calendario (1–90)
     * @return lista de fechas hábiles en formato "YYYY-MM-DD", ordenadas ascendente
     */
    public List<String> getAvailableDays(LocalDate from, int days) {
        List<String> result = new ArrayList<>();
        LocalDate limit = from.plusDays(days);

        for (LocalDate d = from; d.isBefore(limit); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                result.add(d.toString());
            }
        }

        log.debug("Días hábiles desde {} ({} días): {} fechas", from, days, result.size());
        return result;
    }

    /**
     * Retorna los agendamientos de un mes paginados por semana de 7 días.
     * Semana 1 = días 1–7, semana 2 = días 8–14, etc.
     * La última semana se recorta al último día del mes.
     *
     * @param mes    mes en formato YYYY-MM
     * @param semana número de semana dentro del mes (1-indexado)
     * @return DTO con metadatos de paginación y agendamientos agrupados por fecha
     * @throws IllegalArgumentException si el formato de mes es inválido o la semana está fuera de rango
     */
    @Transactional(readOnly = true)
    public AppointmentCalendarDTO getCalendario(String mes, int semana) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato de mes inválido. Use YYYY-MM (ej: 2026-05).");
        }

        int totalSemanas = (int) Math.ceil(yearMonth.lengthOfMonth() / 7.0);

        if (semana < 1 || semana > totalSemanas) {
            throw new IllegalArgumentException(
                "La semana debe estar entre 1 y " + totalSemanas + " para el mes " + mes + ".");
        }

        LocalDate desde = yearMonth.atDay(1).plusDays((long) (semana - 1) * 7);
        LocalDate hasta = desde.plusDays(6);
        if (hasta.isAfter(yearMonth.atEndOfMonth())) {
            hasta = yearMonth.atEndOfMonth();
        }

        List<Appointment> appointments =
            appointmentRepository.findByFechaBetweenOrderByFechaAscHoraAsc(desde, hasta);

        Map<String, List<AppointmentSummaryDTO>> dias = appointments.stream()
            .collect(Collectors.groupingBy(
                a -> a.getFecha().toString(),
                LinkedHashMap::new,
                Collectors.mapping(this::mapToSummary, Collectors.toList())
            ));

        log.debug("Calendario {}, semana {}/{}: {} días con agendamientos ({} → {})",
            mes, semana, totalSemanas, dias.size(), desde, hasta);

        return AppointmentCalendarDTO.builder()
            .mes(mes)
            .semana(semana)
            .totalSemanas(totalSemanas)
            .desde(desde.toString())
            .hasta(hasta.toString())
            .primera(semana == 1)
            .ultima(semana == totalSemanas)
            .dias(dias)
            .build();
    }

    private List<LocalTime> buildSlots() {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime hora = LocalTime.of(9, 0);
        LocalTime fin  = LocalTime.of(18, 0);
        while (hora.isBefore(fin)) {
            slots.add(hora);
            hora = hora.plusHours(1);
        }
        return slots;
    }

    private AppointmentSummaryDTO mapToSummary(Appointment appointment) {
        return AppointmentSummaryDTO.builder()
            .id(appointment.getId())
            .idExterno(appointment.getIdExterno())
            .nombreCliente(appointment.getNombreCliente())
            .email(appointment.getEmail())
            .materia(appointment.getService().getName())
            .fecha(appointment.getFecha())
            .hora(appointment.getHora())
            .monto(appointment.getMonto())
            .estado(appointment.getEstado().name())
            .descripcion(appointment.getDescripcion())
            .build();
    }
}
