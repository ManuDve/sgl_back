package cl.sgl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para confirmar el pago manual de un agendamiento.
 * Solo requiere el número de transacción bancaria; el monto se toma
 * automáticamente del agendamiento para evitar errores de digitación.
 *
 * Historia: SGL-048 PAY-MANUAL-CONF
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank(message = "El número de transacción es obligatorio")
    @Size(min = 4, max = 50,
          message = "El número de transacción debe tener entre 4 y 50 caracteres")
    @Pattern(regexp = "[A-Za-z0-9\\-]+",
             message = "Solo se permiten dígitos, letras y guiones (ej: 123456789 o OPE-20260515)")
    private String codigoTransaccion;
}
