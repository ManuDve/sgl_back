package cl.sgl.repository;

import cl.sgl.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para el historial de notificaciones de email.
 * Historia: SGL-040 NOTIF-AUDIT
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByAppointmentIdOrderByFechaEnvioDesc(Long appointmentId);
}
