package cl.sgl.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para crear un nuevo servicio.
 * Utilizado en POST /api/admin/services
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLegalServiceRequest {

    /**
     * Nombre del servicio (requerido, único)
     */
    @NotBlank(message = "El nombre del servicio es obligatorio")
    private String name;

    /**
     * Descripción del servicio
     */
    private String description;

    /**
     * Precio del servicio en CLP (requerido, mayor a 0)
     */
    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal price;

    /**
     * Indica si el servicio está activo (default: true)
     */
    private Boolean active;
}
