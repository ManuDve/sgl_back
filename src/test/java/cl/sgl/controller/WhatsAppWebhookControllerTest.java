package cl.sgl.controller;

import cl.sgl.service.WhatsAppBotService;
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
 * Historia: SGL-074 WA-MENU, SGL-075 WA-CONSULT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppWebhookController Tests")
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppBotService whatsAppBotService;

    @InjectMocks
    private WhatsAppWebhookController controller;

    @Test
    @DisplayName("handleInbound llama handleMessage con número sin prefijo whatsapp: y body")
    void testHandleInbound_LlamaHandleMessageConDatos() {
        controller.handleInbound("whatsapp:+56912345678", "1");

        verify(whatsAppBotService).handleMessage("+56912345678", "1");
    }

    @Test
    @DisplayName("handleInbound retorna HTTP 200")
    void testHandleInbound_Retorna200() {
        ResponseEntity<Void> resp = controller.handleInbound("whatsapp:+56912345678", "hola");

        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("handleInbound sin From no llama handleMessage y retorna 200")
    void testHandleInbound_SinFrom_NoLlamaBot() {
        ResponseEntity<Void> resp = controller.handleInbound(null, "hola");

        verify(whatsAppBotService, never()).handleMessage(any(), any());
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("handleInbound con From vacío no llama handleMessage")
    void testHandleInbound_FromVacio_NoLlamaBot() {
        controller.handleInbound("  ", "hola");

        verify(whatsAppBotService, never()).handleMessage(any(), any());
    }
}
