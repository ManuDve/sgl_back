package cl.sgl.controller;

import cl.sgl.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook para mensajes entrantes de WhatsApp (Twilio).
 * Twilio envía un POST con form-params cada vez que alguien escribe al número.
 * La respuesta es siempre HTTP 200 vacío; el menú se envía como mensaje saliente.
 *
 * Historia: SGL-074 WA-MENU
 */
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppService whatsAppService;

    /**
     * Recibe mensajes entrantes de Twilio WhatsApp y responde con el menú de opciones.
     *
     * @param from número del remitente en formato "whatsapp:+56XXXXXXXXX"
     * @param body contenido del mensaje recibido (solo para logging)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleInbound(
            @RequestParam(value = "From", required = false) String from,
            @RequestParam(value = "Body", required = false) String body) {

        if (from == null || from.isBlank()) {
            log.warn("Webhook WhatsApp recibido sin campo From");
            return ResponseEntity.ok().build();
        }

        String phone = from.replace("whatsapp:", "").trim();
        log.info("Mensaje entrante WhatsApp de {} — '{}'", phone,
            body != null ? body.substring(0, Math.min(body.length(), 80)) : "");

        whatsAppService.sendMenuMessage(phone);
        return ResponseEntity.ok().build();
    }
}
