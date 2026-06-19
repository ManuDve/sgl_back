package cl.sgl.dto;

import java.util.List;

/**
 * Respuesta paginada del historial de auditoría.
 * Evita exponer la interfaz Page<T> de Spring Data directamente en la API.
 * Historia: SGL-055 ADM-AUDIT
 */
public record AuditPageResponse(
    List<AuditLogDTO> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
