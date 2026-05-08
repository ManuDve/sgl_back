package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para responder con los datos de un servicio.
 * Utilizado en GET, POST, PUT /api/admin/services
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalServiceResponse {

    /**
     * ID del servicio
     */
    private Long id;

    /**
     * Nombre del servicio
     */
    private String name;

    /**
     * Descripción del servicio
     */
    private String description;

    /**
     * Precio del servicio en CLP
     */
    private BigDecimal price;

    /**
     * Indica si el servicio está activo
     */
    private Boolean active;

    /**
     * Fecha y hora de creación
     */
    private LocalDateTime createdAt;

    /**
     * Fecha y hora de última actualización
     */
    private LocalDateTime updatedAt;
}
