package cl.sgl.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para actualizar un servicio existente.
 * Utilizado en PUT /api/admin/services/{id}
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLegalServiceRequest {

    /**
     * Nombre del servicio (opcional)
     */
    private String name;

    /**
     * Descripción del servicio (opcional)
     */
    private String description;

    /**
     * Precio del servicio en CLP (opcional, si se proporciona debe ser mayor a 0)
     */
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal price;

    /**
     * Indica si el servicio está activo (opcional)
     */
    private Boolean active;
}
