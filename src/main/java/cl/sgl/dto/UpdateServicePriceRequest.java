package cl.sgl.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para actualizar el precio de un servicio.
 * El precio se expresa en pesos chilenos (CLP), sin decimales.
 *
 * Historia: SGL-053 ADM-SERV-PRICE
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateServicePriceRequest {

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 5000, message = "El precio mínimo es $5.000 pesos chilenos")
    private Long precio;
}
