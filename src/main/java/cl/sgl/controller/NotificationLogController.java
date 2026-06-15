package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.NotificationLogDTO;
import cl.sgl.service.NotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint protegido para consultar el historial de notificaciones de un agendamiento.
 *
 * GET /api/admin/notifications/log?appointmentId={id}
 *
 * Historia: SGL-040 NOTIF-AUDIT
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Log", description = "Historial de notificaciones por email")
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    @GetMapping("/log")
    @Operation(
        summary = "Historial de notificaciones de un agendamiento",
        description = "Devuelve todos los intentos de envío de email para el agendamiento dado, ordenados por fecha descendente.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<NotificationLogDTO>>> getLog(
            @Parameter(description = "ID interno del agendamiento", required = true)
            @RequestParam Long appointmentId) {

        log.debug("GET /api/admin/notifications/log — appointmentId={}", appointmentId);
        List<NotificationLogDTO> logs = notificationLogService.findByAppointmentId(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(logs, "Historial de notificaciones obtenido"));
    }
}
