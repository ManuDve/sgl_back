package cl.sgl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa un servicio legal ofrecido por el estudio.
 * Ejemplo: "Divorcio", "Herencias", "Cobro de Deuda", etc.
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Entity
@Table(name = "services", uniqueConstraints = {
    @UniqueConstraint(columnNames = "name", name = "uk_service_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre del servicio (ej: "Divorcio Contencioso")
     * Único y no nulo
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Descripción detallada del servicio
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Precio de referencia del servicio en CLP
     */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Indica si el servicio está activo (disponible para agendamiento)
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Fecha y hora de creación
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Fecha y hora de última actualización
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Hook PrePersist para establecer createdAt y updatedAt
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * Hook PreUpdate para actualizar updatedAt
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
