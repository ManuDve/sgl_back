package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de POST /api/appointments/{idExterno}/verify-otp.
 * Historia: SGL-067 GES-VERIFY
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpResponse {

    private String token;
    private String idExterno;
    private int expiresIn;
}
