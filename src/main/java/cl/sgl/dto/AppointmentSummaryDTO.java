package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de resumen para listado de agendamientos en el panel admin.
 *
 * Historia: SGL-045 ADM-LIST-PEND
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentSummaryDTO {

    private Long id;
    private String idExterno;
    private String nombreCliente;
    private String email;
    private String materia;
    private LocalDate fecha;
    private LocalTime hora;
    private BigDecimal monto;
    private String estado;
    private boolean reagendado;
    private String descripcion;
}
