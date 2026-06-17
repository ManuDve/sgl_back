package cl.sgl.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo del endpoint POST /api/appointments/{idExterno}/request-otp.
 * El cliente debe proporcionar email O teléfono para verificar su identidad.
 * Historia: SGL-066 GES-OTP
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequest {

    private String email;

    private String telefono;

    @AssertTrue(message = "Debe proporcionar email o teléfono")
    public boolean isEmailOrTelefonoPresente() {
        return (email != null && !email.isBlank()) ||
               (telefono != null && !telefono.isBlank());
    }
}
