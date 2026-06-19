package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AuditPageResponse;
import cl.sgl.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historia: SGL-055 ADM-AUDIT
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Historial de acciones administrativas")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Listar registros de auditoría paginados")
    public ResponseEntity<ApiResponse<AuditPageResponse>> getAuditLog(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditPageResponse result = auditService.getPage(page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "Registros de auditoría"));
    }
}
