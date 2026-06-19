package cl.sgl.service;

import cl.sgl.dto.AuditLogDTO;
import cl.sgl.dto.AuditPageResponse;
import cl.sgl.entity.AuditLog;
import cl.sgl.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Registra acciones administrativas en audit_log para trazabilidad.
 *
 * Uso en servicios con email conocido (login):
 *   auditService.log("LOGIN", "AUTH", null, email, null);
 *
 * Uso en servicios con sesión JWT activa (todas las demás acciones admin):
 *   auditService.log("CAMBIO_ESTADO", "AGENDAMIENTO", id.toString(), detalles);
 *
 * Historia: SGL-055 ADM-AUDIT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    public static final String ACCION_LOGIN          = "LOGIN";
    public static final String ACCION_CAMBIO_ESTADO  = "CAMBIO_ESTADO";
    public static final String ACCION_CONFIRMAR_PAGO = "CONFIRMAR_PAGO";
    public static final String ACCION_CAMBIO_PRECIO  = "CAMBIO_PRECIO";

    public static final String ENTIDAD_AUTH          = "AUTH";
    public static final String ENTIDAD_AGENDAMIENTO  = "AGENDAMIENTO";
    public static final String ENTIDAD_SERVICIO      = "SERVICIO";

    private final AuditLogRepository auditLogRepository;

    /**
     * Registra una acción con email explícito (para login, donde aún no hay SecurityContext).
     * Corre en transacción propia para no afectar la transacción del llamador.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String accion, String entidad, String entidadId, String adminEmail, String detalles) {
        try {
            AuditLog entry = AuditLog.builder()
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .adminEmail(adminEmail)
                .detalles(detalles)
                .fechaAccion(LocalDateTime.now())
                .build();
            auditLogRepository.save(entry);
            log.info("Audit: {} | {} {} | admin={}", accion, entidad, entidadId, adminEmail);
        } catch (Exception e) {
            log.error("Error al registrar auditoría — accion={} entidad={} : {}", accion, entidad, e.getMessage());
        }
    }

    /**
     * Registra una acción extrayendo el email del SecurityContext (para acciones con JWT activo).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String accion, String entidad, String entidadId, String detalles) {
        log(accion, entidad, entidadId, resolveAdminEmail(), detalles);
    }

    /**
     * Retorna registros de auditoría paginados, orden DESC por fechaAccion.
     */
    @Transactional(readOnly = true)
    public AuditPageResponse getPage(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("fechaAccion").descending());
        Page<AuditLog> result = auditLogRepository.findAll(pageable);
        return new AuditPageResponse(
            result.getContent().stream().map(this::toDTO).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private String resolveAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "sistema";
    }

    private AuditLogDTO toDTO(AuditLog e) {
        return new AuditLogDTO(
            e.getId(), e.getAccion(), e.getEntidad(), e.getEntidadId(),
            e.getAdminEmail(), e.getDetalles(), e.getFechaAccion()
        );
    }
}
