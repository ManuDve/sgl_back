package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO público de servicio legal para el formulario de agendamiento.
 * Solo expone los campos que el cliente necesita ver.
 *
 * Historia: SGL-018 AG-SELECT-MAT
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePublicDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
}
