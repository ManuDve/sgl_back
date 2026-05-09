package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para gestión de agendamientos en el panel admin.
 *
 * Rutas:
 * - GET /api/admin/appointments            → Listar todos los agendamientos
 * - GET /api/admin/appointments?status=pending → Listar agendamientos por estado
 *
 * Historia: SGL-045 ADM-LIST-PEND
 */
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agendamientos", description = "Gestión de agendamientos — panel administrativo")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Lista agendamientos, con filtro opcional por estado.
     * Requiere JWT de administrador.
     *
     * @param status filtro de estado: PENDING | CONFIRMED | CANCELLED | RESCHEDULED (opcional)
     * @return lista de agendamientos ordenada por fecha y hora ascendente
     */
    @GetMapping
    @Operation(
        summary = "Listar agendamientos",
        description = "Retorna agendamientos filtrados por estado. Si no se proporciona `status`, devuelve todos. Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Lista de agendamientos",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Estado inválido"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "No autenticado"
        )
    })
    public ResponseEntity<ApiResponse<List<AppointmentSummaryDTO>>> listAppointments(
        @Parameter(description = "Filtro por estado: PENDING, CONFIRMED, CANCELLED, RESCHEDULED")
        @RequestParam(required = false) String status) {

        log.info("GET /api/admin/appointments - status={}", status);

        List<AppointmentSummaryDTO> appointments = appointmentService.listByStatus(status);
        ApiResponse<List<AppointmentSummaryDTO>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Agendamientos obtenidos exitosamente",
            appointments
        );
        return ResponseEntity.ok(response);
    }
}
