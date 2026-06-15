package cl.sgl.repository;

import cl.sgl.entity.EmailRetryQueue;
import cl.sgl.entity.EstadoRetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para la cola de reintento de emails fallidos.
 * Historia: SGL-038 NOTIF-RETRY
 */
@Repository
public interface EmailRetryQueueRepository extends JpaRepository<EmailRetryQueue, Long> {

    List<EmailRetryQueue> findByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntento(
            EstadoRetry estado, LocalDateTime ahora);
}
