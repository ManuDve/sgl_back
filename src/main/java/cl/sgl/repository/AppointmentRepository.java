package cl.sgl.repository;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para agendamientos.
 *
 * Historia: SGL-045 ADM-LIST-PEND
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByEstadoOrderByFechaAscHoraAsc(AppointmentStatus estado);

    List<Appointment> findAllByOrderByFechaAscHoraAsc();
}
