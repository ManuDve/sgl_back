package cl.sgl.repository;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * Repositorio JPA para agendamientos.
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-021 AG-HORAS
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus estado);

    List<Appointment> findAllByOrderByFechaAscHoraAsc();

    /**
     * Retorna las horas ya reservadas en una fecha dada.
     * Excluye agendamientos CANCELLED porque liberan el slot.
     */
    @Query("SELECT a.hora FROM Appointment a WHERE a.fecha = :fecha AND a.estado != :cancelado")
    List<LocalTime> findBookedHoursForDate(@Param("fecha") LocalDate fecha,
                                           @Param("cancelado") AppointmentStatus cancelado);

    /**
     * Verifica si existe un agendamiento en estado PENDING o CONFIRMED para la fecha y hora dadas.
     * Usado para prevenir doble reserva del mismo slot (SGL-030 AG-CONFLICT).
     */
    boolean existsByFechaAndHoraAndEstadoIn(LocalDate fecha, LocalTime hora, Collection<AppointmentStatus> estados);

    java.util.Optional<Appointment> findByIdExterno(String idExterno);

    /**
     * Retorna todos los agendamientos dentro de un rango de fechas (inclusive en ambos extremos),
     * ordenados por fecha y hora ascendente.
     * Usado por el calendario semanal del panel admin (SGL-049 ADM-CAL).
     */
    List<Appointment> findByFechaBetweenOrderByFechaAscHoraAsc(LocalDate desde, LocalDate hasta);
}
