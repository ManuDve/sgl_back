package cl.sgl.repository;

import cl.sgl.entity.ReminderLog;
import cl.sgl.entity.ReminderTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para el registro de recordatorios enviados.
 * Historia: SGL-035 NOTIF-REMIND
 */
@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    boolean existsByAppointmentIdAndTipo(Long appointmentId, ReminderTipo tipo);
}
