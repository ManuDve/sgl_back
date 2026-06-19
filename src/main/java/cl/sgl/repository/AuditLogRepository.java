package cl.sgl.repository;

import cl.sgl.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Historia: SGL-055 ADM-AUDIT
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAll(Pageable pageable);
}
