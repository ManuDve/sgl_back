package cl.sgl.service;

import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de lógica de negocio para agendamientos.
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-046 ADM-DETAIL, SGL-021 AG-HORAS, SGL-020 AG-FECHAS
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

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
            .fecha(appointment.getFecha())
            .hora(appointment.getHora())
            .monto(appointment.getMonto())
            .estado(appointment.getEstado().name())
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
            .build();
    }
}
