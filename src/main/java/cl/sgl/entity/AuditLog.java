package cl.sgl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de auditoría de acciones administrativas.
 * Historia: SGL-055 ADM-AUDIT
 */
@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String accion;

    @Column(nullable = false, length = 50)
    private String entidad;

    @Column(name = "entidad_id", length = 50)
    private String entidadId;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Column(columnDefinition = "TEXT")
    private String detalles;

    @Column(name = "fecha_accion", nullable = false)
    private LocalDateTime fechaAccion;
}
