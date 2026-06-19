package cl.sgl.dto;

import java.time.LocalDateTime;

/**
 * Historia: SGL-055 ADM-AUDIT
 */
public record AuditLogDTO(
    Long          id,
    String        accion,
    String        entidad,
    String        entidadId,
    String        adminEmail,
    String        detalles,
    LocalDateTime fechaAccion
) {}
