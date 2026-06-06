package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO de detalle completo de un agendamiento para el panel admin.
 *
 * Historia: SGL-046 ADM-DETAIL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentDetailDTO {

    private Long id;
    private String idExterno;

    // Datos del cliente
    private String nombreCliente;
    private String email;
    private String telefono;

    // Servicio contratado
    private Long servicioId;
    private String materia;
    private String descripcionServicio;

    // Notas del cliente al agendar
    private String descripcion;

    // Cita
    private LocalDate fecha;
    private LocalTime hora;
    private BigDecimal monto;
    private String estado;

    // Confirmación de pago (null si no se ha confirmado)
    private String codigoTransaccion;
    private BigDecimal montoConfirmado;
    private LocalDateTime fechaPago;

    // Auditoría
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
