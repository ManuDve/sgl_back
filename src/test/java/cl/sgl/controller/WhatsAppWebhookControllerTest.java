package cl.sgl.controller;

import cl.sgl.service.WhatsAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para WhatsAppWebhookController.
 * Historia: SGL-074 WA-MENU
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppWebhookController Tests")
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppService whatsAppService;

    @InjectMocks
    private WhatsAppWebhookController controller;

    @Test
    @DisplayName("handleInbound llama sendMenuMessage con número sin prefijo whatsapp:")
    void testHandleInbound_LlamaSendMenuConNumeroSinPrefijo() {
        controller.handleInbound("whatsapp:+56912345678", "hola");

        verify(whatsAppService).sendMenuMessage("+56912345678");
    }

    @Test
    @DisplayName("handleInbound retorna HTTP 200")
    void testHandleInbound_Retorna200() {
        ResponseEntity<Void> resp = controller.handleInbound("whatsapp:+56912345678", "cualquier texto");

        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("handleInbound sin From no llama sendMenuMessage y retorna 200")
    void testHandleInbound_SinFrom_NoLlamaSendMenu() {
        ResponseEntity<Void> resp = controller.handleInbound(null, "hola");

        verify(whatsAppService, never()).sendMenuMessage(any());
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("handleInbound con From vacío no llama sendMenuMessage")
    void testHandleInbound_FromVacio_NoLlamaSendMenu() {
        controller.handleInbound("  ", "hola");

        verify(whatsAppService, never()).sendMenuMessage(any());
    }

}
