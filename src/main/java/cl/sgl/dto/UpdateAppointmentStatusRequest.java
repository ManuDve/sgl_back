package cl.sgl.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para cambiar el estado de un agendamiento.
 * Acepta valores en español o inglés (PENDIENTE/PENDING, CONFIRMADO/CONFIRMED,
 * CANCELADO/CANCELLED, REAGENDADO/RESCHEDULED).
 *
 * Historia: SGL-047 ADM-STATE
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    @NotBlank(message = "El estado es obligatorio")
    private String estado;
}
