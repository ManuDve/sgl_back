package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para entradas del historial de notificaciones.
 * Historia: SGL-040 NOTIF-AUDIT
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogDTO {
    private Long          id;
    private Long          appointmentId;
    private String        tipo;
    private String        canal;
    private String        destinatario;
    private String        estado;
    private LocalDateTime fechaEnvio;
    private String        error;
}
