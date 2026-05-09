package cl.sgl.service;

import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de lógica de negocio para agendamientos.
 *
 * Historia: SGL-045 ADM-LIST-PEND
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
