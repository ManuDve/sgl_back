package cl.sgl.service;

import cl.sgl.dto.NotificationLogDTO;
import cl.sgl.entity.NotificationLog;
import cl.sgl.entity.TipoEmail;
import cl.sgl.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Registra el resultado de cada intento de envío de email.
 * Los errores de persistencia se absorben para no interrumpir el flujo principal.
 *
 * Historia: SGL-040 NOTIF-AUDIT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationLogService {

    static final String  CANAL_EMAIL  = "EMAIL";
    static final String  ESTADO_OK    = "ENVIADO";
    static final String  ESTADO_FAIL  = "FALLIDO";
    private static final ZoneId ZONE_CL = ZoneId.of("America/Santiago");

    private final NotificationLogRepository repository;

    public void logSuccess(Long appointmentId, TipoEmail tipo, String destinatario) {
        persist(appointmentId, tipo, destinatario, ESTADO_OK, null);
    }

    public void logFailure(Long appointmentId, TipoEmail tipo, String destinatario, String error) {
        persist(appointmentId, tipo, destinatario, ESTADO_FAIL, error);
    }

    public List<NotificationLogDTO> findByAppointmentId(Long appointmentId) {
        return repository.findByAppointmentIdOrderByFechaEnvioDesc(appointmentId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private void persist(Long appointmentId, TipoEmail tipo, String destinatario,
                         String estado, String error) {
        try {
            repository.save(NotificationLog.builder()
                .appointmentId(appointmentId)
                .tipo(tipo)
                .canal(CANAL_EMAIL)
                .destinatario(destinatario)
                .estado(estado)
                .fechaEnvio(LocalDateTime.now(ZONE_CL))
                .error(error)
                .build());
        } catch (Exception e) {
            log.error("No se pudo registrar notificación — tipo={} appt={} — {}",
                tipo, appointmentId, e.getMessage());
        }
    }

    private NotificationLogDTO toDTO(NotificationLog entry) {
        return NotificationLogDTO.builder()
            .id(entry.getId())
            .appointmentId(entry.getAppointmentId())
            .tipo(entry.getTipo().name())
            .canal(entry.getCanal())
            .destinatario(entry.getDestinatario())
            .estado(entry.getEstado())
            .fechaEnvio(entry.getFechaEnvio())
            .error(entry.getError())
            .build();
    }
}
