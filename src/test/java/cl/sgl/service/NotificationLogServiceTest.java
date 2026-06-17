package cl.sgl.service;

import cl.sgl.dto.NotificationLogDTO;
import cl.sgl.entity.NotificationLog;
import cl.sgl.entity.TipoEmail;
import cl.sgl.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para NotificationLogService.
 * Historia: SGL-040 NOTIF-AUDIT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationLogService Tests")
class NotificationLogServiceTest {

    @Mock
    private NotificationLogRepository repository;

    @InjectMocks
    private NotificationLogService service;

    // ── logSuccess ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("logSuccess guarda registro con estado ENVIADO, canal EMAIL y destinatario correcto")
    void testLogSuccess_GuardaRegistroConCamposCorrectos() {
        service.logSuccess(1L, TipoEmail.CONFIRMACION_CLIENTE, "cliente@test.cl");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertEquals(1L,                              saved.getAppointmentId());
        assertEquals(TipoEmail.CONFIRMACION_CLIENTE,  saved.getTipo());
        assertEquals(NotificationLogService.CANAL_EMAIL, saved.getCanal());
        assertEquals("cliente@test.cl",               saved.getDestinatario());
        assertEquals(NotificationLogService.ESTADO_OK, saved.getEstado());
        assertNull(saved.getError());
        assertNotNull(saved.getFechaEnvio());
    }

    @Test
    @DisplayName("logSuccess no lanza excepción si el repositorio falla")
    void testLogSuccess_ExcepcionEnRepositorio_NoLanzaExcepcion() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> service.logSuccess(1L, TipoEmail.NOTIF_ADMIN, "admin@test.cl"));
    }

    // ── logFailure ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("logFailure guarda registro con estado FALLIDO y el mensaje de error")
    void testLogFailure_GuardaRegistroConEstadoFallidoYError() {
        service.logFailure(2L, TipoEmail.REMINDER_24H, "cliente@test.cl", "Connection refused");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertEquals(NotificationLogService.ESTADO_FAIL, saved.getEstado());
        assertEquals("Connection refused",               saved.getError());
        assertEquals(TipoEmail.REMINDER_24H,             saved.getTipo());
    }

    @Test
    @DisplayName("logFailure guarda registro con error null cuando el mensaje de error es null")
    void testLogFailure_ErrorNulo_GuardaErrorNulo() {
        service.logFailure(1L, TipoEmail.CONFIRMACION_CLIENTE, "a@b.cl", null);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getError());
    }

    @Test
    @DisplayName("logFailure no lanza excepción si el repositorio falla")
    void testLogFailure_ExcepcionEnRepositorio_NoLanzaExcepcion() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() ->
            service.logFailure(1L, TipoEmail.NOTIF_ADMIN, "admin@test.cl", "timeout"));
    }

    // ── logSuccess/logFailure con canal explícito (sobrecarga WHATSAPP) ────

    @Test
    @DisplayName("logSuccess con canal WHATSAPP guarda el canal correcto")
    void testLogSuccess_ConCanalWhatsApp_GuardaCanalWhatsApp() {
        service.logSuccess(3L, TipoEmail.CONFIRMACION_CLIENTE,
            NotificationLogService.CANAL_WHATSAPP, "+56912345678");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertEquals(NotificationLogService.CANAL_WHATSAPP, saved.getCanal());
        assertEquals(NotificationLogService.ESTADO_OK,      saved.getEstado());
        assertEquals("+56912345678",                        saved.getDestinatario());
        assertNull(saved.getError());
    }

    @Test
    @DisplayName("logFailure con canal WHATSAPP guarda el canal y el error")
    void testLogFailure_ConCanalWhatsApp_GuardaCanalYError() {
        service.logFailure(4L, TipoEmail.CONFIRMACION_CLIENTE,
            NotificationLogService.CANAL_WHATSAPP, "+56912345678", "Twilio timeout");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertEquals(NotificationLogService.CANAL_WHATSAPP, saved.getCanal());
        assertEquals(NotificationLogService.ESTADO_FAIL,    saved.getEstado());
        assertEquals("Twilio timeout",                      saved.getError());
    }

    @Test
    @DisplayName("logSuccess con canal explícito no lanza excepción si el repositorio falla")
    void testLogSuccess_ConCanalExplicito_ExcepcionEnRepositorio_NoLanzaExcepcion() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> service.logSuccess(1L, TipoEmail.CONFIRMACION_CLIENTE,
            NotificationLogService.CANAL_WHATSAPP, "+56912345678"));
    }

    @Test
    @DisplayName("logFailure con canal explícito no lanza excepción si el repositorio falla")
    void testLogFailure_ConCanalExplicito_ExcepcionEnRepositorio_NoLanzaExcepcion() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> service.logFailure(1L, TipoEmail.CONFIRMACION_CLIENTE,
            NotificationLogService.CANAL_WHATSAPP, "+56912345678", "timeout"));
    }

    // ── findByAppointmentId ────────────────────────────────────────────────

    @Test
    @DisplayName("findByAppointmentId retorna lista mapeada a DTOs correctamente")
    void testFindByAppointmentId_RetornaListaDeMapeadaADTOs() {
        NotificationLog entry = NotificationLog.builder()
            .id(10L)
            .appointmentId(5L)
            .tipo(TipoEmail.CONFIRMACION_CLIENTE)
            .canal("EMAIL")
            .destinatario("cli@test.cl")
            .estado("ENVIADO")
            .fechaEnvio(LocalDateTime.of(2026, 6, 15, 10, 0))
            .error(null)
            .build();
        when(repository.findByAppointmentIdOrderByFechaEnvioDesc(5L)).thenReturn(List.of(entry));

        List<NotificationLogDTO> result = service.findByAppointmentId(5L);

        assertEquals(1, result.size());
        NotificationLogDTO dto = result.get(0);
        assertEquals(10L,                        dto.getId());
        assertEquals(5L,                         dto.getAppointmentId());
        assertEquals("CONFIRMACION_CLIENTE",     dto.getTipo());
        assertEquals("EMAIL",                    dto.getCanal());
        assertEquals("cli@test.cl",              dto.getDestinatario());
        assertEquals("ENVIADO",                  dto.getEstado());
        assertEquals(LocalDateTime.of(2026,6,15,10,0), dto.getFechaEnvio());
        assertNull(dto.getError());
    }

    @Test
    @DisplayName("findByAppointmentId retorna lista vacía cuando no hay registros")
    void testFindByAppointmentId_SinRegistros_RetornaListaVacia() {
        when(repository.findByAppointmentIdOrderByFechaEnvioDesc(99L)).thenReturn(List.of());

        List<NotificationLogDTO> result = service.findByAppointmentId(99L);

        assertTrue(result.isEmpty());
        verify(repository).findByAppointmentIdOrderByFechaEnvioDesc(99L);
    }
}
