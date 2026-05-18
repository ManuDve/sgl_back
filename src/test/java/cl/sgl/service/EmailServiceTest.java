package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.MailtrapMail;
import io.mailtrap.model.response.emails.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para EmailService.
 * Historia: SGL-033 NOTIF-EMAIL-01
 */
@DisplayName("EmailService Tests")
class EmailServiceTest {

    private MailtrapClient mockClient;
    private EmailService   emailService;
    private Appointment    appointment;

    @BeforeEach
    void setUp() {
        mockClient   = mock(MailtrapClient.class);
        emailService = new EmailService(mockClient, "no-reply@sgl.cl");

        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio Contencioso")
            .price(new BigDecimal("500000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        appointment = Appointment.builder()
            .id(1L)
            .idExterno("AG-ABCD-0001")
            .nombreCliente("Juan Pérez")
            .email("juan.perez@example.cl")
            .telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 10))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("500000"))
            .estado(AppointmentStatus.PENDING)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("sendConfirmationEmail llama a mailtrapClient.send con los datos correctos")
    void testSendConfirmationEmail_DatosCorrectos() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenReturn(mock(SendResponse.class));

        emailService.sendConfirmationEmail(appointment);

        ArgumentCaptor<MailtrapMail> captor = ArgumentCaptor.forClass(MailtrapMail.class);
        verify(mockClient).send(captor.capture());

        MailtrapMail mail = captor.getValue();
        assertEquals("juan.perez@example.cl", mail.getTo().get(0).getEmail());
        assertEquals("no-reply@sgl.cl",        mail.getFrom().getEmail());
        assertEquals("Estudio Jurídico SGL",  mail.getFrom().getName());
        assertTrue(mail.getSubject().contains("Confirmación"));
        assertTrue(mail.getHtml().contains("AG-ABCD-0001"),  "El HTML debe incluir el idExterno");
        assertTrue(mail.getHtml().contains("Juan Pérez"),    "El HTML debe incluir el nombre del cliente");
        assertTrue(mail.getHtml().contains("Divorcio"),      "El HTML debe incluir el nombre del servicio");
    }

    @Test
    @DisplayName("sendConfirmationEmail no lanza excepción si el cliente falla")
    void testSendConfirmationEmail_ErrorNoLanzaExcepcion() throws Exception {
        when(mockClient.send(any(MailtrapMail.class))).thenThrow(new RuntimeException("Mailtrap no disponible"));

        assertDoesNotThrow(() -> emailService.sendConfirmationEmail(appointment));
        verify(mockClient).send(any(MailtrapMail.class));
    }
}
