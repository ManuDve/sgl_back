package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta del endpoint POST /api/webpay/init.
 * El frontend usa token + url para redirigir al formulario de Transbank.
 *
 * Historia: SGL-080 PAY-POC
 */
@Data
@AllArgsConstructor
public class WebpayInitResponse {
    /** Token de la transacción (válido 5 minutos). */
    private String token;
    /** URL del formulario de pago de Transbank a la que se redirige al usuario. */
    private String url;
}
