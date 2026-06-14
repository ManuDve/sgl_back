package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AppointmentCalendarDTO;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
     * Retorna los agendamientos de un mes paginados por semana.
     * Semana 1 = días 1–7, semana 2 = días 8–14, …, hasta recortado al fin de mes.
     * El campo `totalSemanas` permite al frontend construir la navegación prev/next.
     */
    @GetMapping("/calendario")
    @Operation(
        summary = "Calendario de agendamientos",
        description = "Retorna agendamientos del mes paginados por semana de 7 días. " +
            "`semana` es 1-indexado: semana 1 = días 1–7, semana 2 = días 8–14, etc. " +
            "La última semana se recorta al último día del mes. " +
            "`totalSemanas` indica el límite de navegación. " +
            "Solo se incluyen fechas que tienen al menos un agendamiento. " +
            "Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Calendario obtenido exitosamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Formato de mes inválido o semana fuera de rango"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "No autenticado"
        )
    })
    public ResponseEntity<ApiResponse<AppointmentCalendarDTO>> getCalendario(
        @Parameter(description = "Mes en formato YYYY-MM", required = true, example = "2026-05")
        @RequestParam String mes,
        @Parameter(description = "Número de semana dentro del mes (1-indexado, por defecto 1)", example = "1")
        @RequestParam(defaultValue = "1") int semana) {

        log.info("GET /api/admin/appointments/calendario - mes={}, semana={}", mes, semana);

        AppointmentCalendarDTO calendario = appointmentService.getCalendario(mes, semana);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Calendario obtenido exitosamente",
            calendario
        ));
    }

    /**
     * Lista agendamientos con filtros opcionales combinables.
     * Todos los parámetros son opcionales; omitirlos equivale a "sin filtro".
     * El parámetro legacy `status` se mantiene por compatibilidad; `estado` tiene precedencia.
     *
     * @param search texto libre buscado en nombre, email e idExterno
     * @param estado filtro de estado (PENDING / CONFIRMED / CANCELLED / RESCHEDULED, inglés o español)
     * @param status alias legacy de `estado` (para compatibilidad con clientes anteriores)
     * @param desde  fecha mínima inclusiva en formato YYYY-MM-DD
     * @param hasta  fecha máxima inclusiva en formato YYYY-MM-DD
     */
    @GetMapping
    @Operation(
        summary = "Listar agendamientos con filtros",
        description = "Retorna agendamientos aplicando filtros opcionales combinables. " +
            "`search` busca en nombre, email e idExterno (case-insensitive). " +
            "`estado` filtra por estado (acepta inglés o español). " +
            "`desde` / `hasta` acotan el rango de fechas (YYYY-MM-DD). " +
            "Sin parámetros devuelve todos los agendamientos. " +
            "El parámetro legacy `status` se mantiene por compatibilidad. " +
            "Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Lista de agendamientos",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Estado inválido o formato de fecha incorrecto"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "No autenticado"
        )
    })
    public ResponseEntity<ApiResponse<List<AppointmentSummaryDTO>>> listAppointments(
        @Parameter(description = "Texto libre: busca en nombre, email e idExterno")
        @RequestParam(required = false) String search,
        @Parameter(description = "Filtro por estado: PENDING, CONFIRMED, CANCELLED, RESCHEDULED (inglés o español)")
        @RequestParam(required = false) String estado,
        @Parameter(description = "Alias legacy de estado (para compatibilidad con versiones anteriores)")
        @RequestParam(required = false) String status,
        @Parameter(description = "Fecha mínima inclusiva (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @Parameter(description = "Fecha máxima inclusiva (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        // estado tiene precedencia sobre el alias legacy status
        String statusFilter = (estado != null) ? estado : status;

        log.info("GET /api/admin/appointments - search={}, estado={}, desde={}, hasta={}",
            search, statusFilter, desde, hasta);

        List<AppointmentSummaryDTO> appointments =
            appointmentService.search(search, statusFilter, desde, hasta);

        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Agendamientos obtenidos exitosamente",
            appointments
        ));
    }

    /**
     * Exporta agendamientos en formato CSV con los mismos filtros que el endpoint de listado.
     * Spring MVC resuelve /export antes que /{id} porque los paths literales tienen mayor precedencia.
     * Requiere JWT de administrador.
     *
     * Historia: SGL-051 ADM-EXPORT
     */
    @GetMapping("/export")
    @Operation(
        summary = "Exportar agendamientos a CSV",
        description = "Genera y descarga un archivo CSV con los agendamientos que cumplan los filtros. " +
            "Acepta los mismos parámetros que el endpoint de listado. " +
            "Sin parámetros exporta todos los agendamientos. " +
            "Requiere autenticación de administrador."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Archivo CSV generado exitosamente"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Estado inválido o formato de fecha incorrecto"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "No autenticado"
        )
    })
    public ResponseEntity<byte[]> exportCsv(
        @Parameter(description = "Texto libre: busca en nombre, email e idExterno")
        @RequestParam(required = false) String search,
        @Parameter(description = "Filtro por estado (inglés o español)")
        @RequestParam(required = false) String estado,
        @Parameter(description = "Alias legacy de estado")
        @RequestParam(required = false) String status,
        @Parameter(description = "Fecha mínima inclusiva (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @Parameter(description = "Fecha máxima inclusiva (YYYY-MM-DD)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        String statusFilter = (estado != null) ? estado : status;

        log.info("GET /api/admin/appointments/export - search={}, estado={}, desde={}, hasta={}",
            search, statusFilter, desde, hasta);

        String csv = appointmentService.exportCsv(search, statusFilter, desde, hasta);
        String filename = "agendamientos_" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(csv.getBytes(StandardCharsets.UTF_8));
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
