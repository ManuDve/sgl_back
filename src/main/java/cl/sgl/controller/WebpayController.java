package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.WebpayInitResponse;
import cl.sgl.entity.Appointment;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.service.AppointmentService;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PoC de integración con Transbank WebpayPlus.
 *
 * Flujo:
 *  1. POST /api/webpay/init?idExterno=AG-XXXX-NNNN → retorna token + url de Transbank
 *  2. Frontend redirige al usuario a Transbank (form POST con token_ws)
 *  3. Usuario paga, Transbank hace POST a /api/webpay/commit con token_ws
 *  4. Backend confirma la transacción y redirige al frontend
 *
 * Historia: SGL-080 PAY-POC
 */
@RestController
@RequestMapping("/api/webpay")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webpay", description = "Integración Transbank WebpayPlus — PoC")
public class WebpayController {

    private final WebpayPlus.Transaction webpayTransaction;
    private final AppointmentRepository  appointmentRepository;
    private final AppointmentService     appointmentService;

    @Value("${transbank.return-url}")
    private String returnUrl;

    @Value("${transbank.frontend-url}")
    private String frontendUrl;

    /**
     * Mapa token → idExterno para recuperar el idExterno cuando Transbank cancela y
     * no envía TBK_ORDER_ID (comportamiento intermitente en sandbox).
     */
    private final ConcurrentHashMap<String, String> pendingTokens = new ConcurrentHashMap<>();

    /**
     * Inicia una transacción WebpayPlus para el agendamiento indicado.
     * Retorna el token y la URL del formulario de pago de Transbank.
     */
    @PostMapping("/init")
    @Operation(
        summary = "Iniciar pago con Transbank",
        description = "Crea una transacción WebpayPlus para el agendamiento. " +
            "El frontend debe redirigir al usuario a la URL retornada con el token como campo hidden."
    )
    public ResponseEntity<ApiResponse<WebpayInitResponse>> init(
            @RequestParam String idExterno) {

        log.info("POST /api/webpay/init — idExterno: {}", idExterno);

        Appointment appointment = appointmentRepository.findByIdExterno(idExterno)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Agendamiento '" + idExterno + "' no encontrado"));

        try {
            WebpayPlusTransactionCreateResponse tbkResponse = webpayTransaction.create(
                idExterno,                            // buyOrder (máx 26 chars)
                "sgl-" + idExterno,                   // sessionId
                appointment.getMonto().doubleValue(), // amount en CLP
                returnUrl                             // URL de retorno POST
            );

            // Guardar token → idExterno como fallback en caso de que TBK_ORDER_ID no llegue al cancelar
            pendingTokens.put(tbkResponse.getToken(), idExterno);

            log.info("Transacción Webpay creada — buyOrder: {}, token: {}",
                idExterno, tbkResponse.getToken());

            return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                "Transacción iniciada",
                new WebpayInitResponse(tbkResponse.getToken(), tbkResponse.getUrl())
            ));

        } catch (Exception e) {
            log.error("Error al crear transacción Webpay para {}: {}", idExterno, e.getMessage());
            throw new IllegalArgumentException("No se pudo iniciar el pago con Transbank: " + e.getMessage());
        }
    }

    /**
     * Recibe el callback de Transbank después del pago.
     * Acepta GET y POST porque Transbank puede usar ambos métodos según el navegador y la versión.
     *
     * Casos posibles:
     * - token_ws presente → transacción completada (aprobada o rechazada por el banco)
     * - TBK_TOKEN presente → usuario canceló manualmente o la sesión expiró (5 min)
     */
    @RequestMapping(value = "/commit", method = {
        org.springframework.web.bind.annotation.RequestMethod.GET,
        org.springframework.web.bind.annotation.RequestMethod.POST
    })
    @Operation(summary = "Callback de Transbank", description = "Endpoint receptor del redirect de Transbank tras el pago. Acepta GET y POST.")
    public void commit(
            @RequestParam(value = "token_ws",     required = false) String tokenWs,
            @RequestParam(value = "TBK_TOKEN",    required = false) String tbkToken,
            @RequestParam(value = "TBK_ORDER_ID", required = false) String tbkOrderId,
            HttpServletResponse response) throws IOException {

        // Cancelación o timeout: Transbank envía TBK_TOKEN y (a veces) TBK_ORDER_ID
        if (tokenWs == null) {
            // Preferir TBK_ORDER_ID (buyOrder directo); si no llega, buscar en caché local
            String idExterno = (tbkOrderId != null && !tbkOrderId.isBlank())
                ? tbkOrderId
                : resolveIdFromTbkToken(tbkToken);
            log.warn("Pago cancelado o expirado — idExterno: {}, TBK_TOKEN: {}", idExterno, tbkToken);

            String redirect = frontendUrl + "/confirmacion?pago=cancelado";
            if (idExterno != null && !idExterno.isBlank()) redirect += "&id=" + idExterno;
            response.sendRedirect(redirect);
            return;
        }

        // Intentar extraer el buyOrder del token antes del commit (por si commit() falla)
        String idExternoFallback = resolveIdFromTbkToken(tokenWs);

        try {
            WebpayPlusTransactionCommitResponse tbkResponse = webpayTransaction.commit(tokenWs);
            String idExterno = tbkResponse.getBuyOrder();

            boolean aprobado = tbkResponse.getResponseCode() == 0
                && "AUTHORIZED".equals(tbkResponse.getStatus());

            if (aprobado) {
                appointmentService.confirmWebpayPayment(
                    idExterno,
                    tbkResponse.getAuthorizationCode(),
                    BigDecimal.valueOf(tbkResponse.getAmount())
                );
                log.info("Pago aprobado — idExterno: {}, auth: {}, monto: {}",
                    idExterno, tbkResponse.getAuthorizationCode(), tbkResponse.getAmount());
                // Redirigir sin param "pago" — el frontend determina éxito desde apt.estado del servidor
                response.sendRedirect(frontendUrl + "/confirmacion?id=" + idExterno);
            } else {
                log.warn("Pago rechazado — idExterno: {}, responseCode: {}, status: {}",
                    idExterno, tbkResponse.getResponseCode(), tbkResponse.getStatus());
                response.sendRedirect(frontendUrl + "/confirmacion?id=" + idExterno + "&pago=rechazado");
            }

        } catch (Exception e) {
            log.error("Error en commit Webpay — token: {}, error: {}", tokenWs, e.getMessage());
            String safeId = (tbkOrderId != null && !tbkOrderId.isBlank()) ? tbkOrderId : idExternoFallback;
            String redirect = frontendUrl + "/confirmacion?pago=error";
            if (safeId != null && !safeId.isBlank()) redirect += "&id=" + safeId;
            response.sendRedirect(redirect);
        }
    }

    /**
     * Intenta resolver el idExterno desde el token: primero en caché local (guardada al hacer init),
     * luego consultando la API de Transbank. Retorna null si no es posible resolver.
     */
    private String resolveIdFromTbkToken(String tbkToken) {
        if (tbkToken == null) return null;

        // Caché local: siempre disponible mientras el proceso no se haya reiniciado
        String cached = pendingTokens.remove(tbkToken);
        if (cached != null) {
            log.debug("idExterno resuelto desde caché local para token {}", tbkToken.substring(0, 8) + "...");
            return cached;
        }

        // Fallback: consultar API de Transbank (puede fallar en transacciones canceladas)
        try {
            var status = webpayTransaction.status(tbkToken);
            return status.getBuyOrder();
        } catch (Exception e) {
            log.warn("No se pudo obtener buyOrder desde TBK_TOKEN (caché vacía): {}", e.getMessage());
            return null;
        }
    }
}
