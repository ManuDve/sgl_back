package cl.sgl.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body para POST /api/appointments/{idExterno}/verify-otp.
 * Historia: SGL-067 GES-VERIFY
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "El código OTP es obligatorio")
    private String otp;
}
