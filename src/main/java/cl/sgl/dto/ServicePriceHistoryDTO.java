package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para un registro del historial de precios.
 * Historia: SGL-053 ADM-SERV-PRICE
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePriceHistoryDTO {

    private Long id;
    private Long servicioId;
    private String nombreServicio;
    private BigDecimal precioAnterior;
    private BigDecimal precioNuevo;
    private LocalDateTime fechaCambio;
}
