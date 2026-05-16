package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.AppointmentSummaryDTO;
import cl.sgl.dto.ConfirmPaymentRequest;
import cl.sgl.dto.UpdateAppointmentStatusRequest;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para gestión de agendamientos en el panel admin.
 *
 * Rutas:
 * - GET   /api/admin/appointments                  → Listar todos los agendamientos
 * - GET   /api/admin/appointments?status=X          → Listar por estado
 * - GET   /api/admin/appointments/{id}              → Detalle completo
 * - PATCH /api/admin/appointments/{id}/estado       → Cambiar estado
 * - PATCH /api/admin/appointments/{id}/pago         → Confirmar pago manual
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-046 ADM-DETAIL, SGL-047 ADM-STATE, SGL-048 PAY-MANUAL-CONF
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

    /**
     * Retorna el detalle completo de un agendamiento por ID.
     * Requiere JWT de administrador.
     *
     * @param id ID interno del agendamiento
     * @return detalle completo o 404 si no existe
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Detalle de agendamiento",
        description = "Retorna todos los campos de un agendamiento por su ID interno. Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Agendamiento encontrado",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Agendamiento no encontrado"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "No autenticado"
        )
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> getAppointmentById(
        @Parameter(description = "ID interno del agendamiento")
        @PathVariable Long id) {

        log.info("GET /api/admin/appointments/{}", id);

        AppointmentDetailDTO detail = appointmentService.getById(id);
        ApiResponse<AppointmentDetailDTO> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Agendamiento obtenido exitosamente",
            detail
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/estado")
    @Operation(
        summary = "Cambiar estado de agendamiento",
        description = "Actualiza el estado de un agendamiento. Acepta español (PENDIENTE, CONFIRMADO, CANCELADO, REAGENDADO) o inglés (PENDING, CONFIRMED, CANCELLED, RESCHEDULED). Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Estado actualizado correctamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Estado inválido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Agendamiento no encontrado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "No autenticado")
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> updateStatus(
        @Parameter(description = "ID interno del agendamiento") @PathVariable Long id,
        @Valid @RequestBody UpdateAppointmentStatusRequest request) {

        log.info("PATCH /api/admin/appointments/{}/estado - nuevo estado: {}", id, request.getEstado());

        AppointmentDetailDTO updated = appointmentService.updateStatus(id, request);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Estado actualizado exitosamente",
            updated
        ));
    }

    @PatchMapping("/{id}/pago")
    @Operation(
        summary = "Confirmar pago manual",
        description = "Registra el número de transacción y monto de la transferencia bancaria. Cambia el estado a CONFIRMED automáticamente. Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Pago confirmado correctamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Campos inválidos o agendamiento cancelado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Agendamiento no encontrado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "No autenticado")
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> confirmPayment(
        @Parameter(description = "ID interno del agendamiento") @PathVariable Long id,
        @Valid @RequestBody ConfirmPaymentRequest request) {

        log.info("PATCH /api/admin/appointments/{}/pago - txn: {}", id, request.getCodigoTransaccion());

        AppointmentDetailDTO updated = appointmentService.confirmPayment(id, request);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Pago confirmado exitosamente",
            updated
        ));
    }
}
