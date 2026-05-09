package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.CreateAppointmentRequest;
import cl.sgl.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Endpoints públicos del flujo de agendamiento.
 * No requieren autenticación JWT.
 *
 * Rutas:
 * - POST /api/appointments                                   → crear agendamiento
 * - GET  /api/appointments/ping                              → health check
 * - GET  /api/appointments/hours-available?date=...          → horas disponibles
 * - GET  /api/appointments/days-available?from=...&days=30   → días hábiles disponibles
 *
 * Historias: SGL-016 AG-NOLOGIN, SGL-021 AG-HORAS, SGL-020 AG-FECHAS, SGL-024 AG-IDEXTERNO
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agendamiento público", description = "Endpoints públicos para el flujo de agendamiento de clientes")
public class PublicAppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @Operation(
        summary = "Crear agendamiento",
        description = """
            Crea un nuevo agendamiento público. Genera un idExterno en formato AG-XXXX-NNNN,
            persiste con estado PENDING y retorna el agendamiento completo.
            No requiere autenticación.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Agendamiento creado exitosamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Campos inválidos, servicio inactivo o slot ocupado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Servicio no encontrado")
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> createAppointment(
        @Valid @RequestBody CreateAppointmentRequest request) {

        log.info("POST /api/appointments - {} | {} {}", request.getNombreCliente(),
            request.getFecha(), request.getHora());

        AppointmentDetailDTO created = appointmentService.createAppointment(request);
        ApiResponse<AppointmentDetailDTO> response = new ApiResponse<>(
            HttpStatus.CREATED.value(),
            "Agendamiento creado exitosamente",
            created
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/ping")
    @Operation(
        summary = "Health check del módulo de agendamiento",
        description = "Confirma que el módulo está disponible. No requiere autenticación."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Módulo disponible")
    })
    public ResponseEntity<Map<String, String>> ping() {
        log.debug("GET /api/appointments/ping");
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Agendamiento público disponible"
        ));
    }

    @GetMapping("/hours-available")
    @Operation(
        summary = "Horas disponibles para una fecha",
        description = """
            Retorna los slots horarios disponibles para la fecha indicada.
            Horario base: 09:00–17:00, intervalos de 60 minutos (America/Santiago).
            Excluye los slots ocupados por agendamientos activos (PENDING, CONFIRMED, RESCHEDULED).
            No requiere autenticación.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Lista de horas disponibles en formato HH:mm",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Fecha inválida o ausente"
        )
    })
    public ResponseEntity<ApiResponse<List<String>>> getHoursAvailable(
        @Parameter(description = "Fecha en formato YYYY-MM-DD", example = "2026-05-15", required = true)
        @RequestParam String date) {

        log.info("GET /api/appointments/hours-available - date={}", date);

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Formato de fecha inválido. Use YYYY-MM-DD.",
                (String) null
            ));
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        if (fecha.isBefore(hoy)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "No se pueden consultar horas para fechas pasadas.",
                (String) null
            ));
        }

        List<String> horas = appointmentService.getAvailableHours(fecha);
        ApiResponse<List<String>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Horas disponibles obtenidas exitosamente",
            horas
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/days-available")
    @Operation(
        summary = "Días hábiles disponibles",
        description = """
            Retorna los días hábiles (lunes a viernes) dentro de una ventana de días calendario.
            - `from`: fecha de inicio en YYYY-MM-DD. Opcional; por defecto hoy en America/Santiago.
            - `days`: ventana en días calendario (1–90). Opcional; por defecto 30.
            No incluye fines de semana ni fechas pasadas. No requiere autenticación.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Lista de fechas hábiles en formato YYYY-MM-DD",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Parámetros inválidos")
    })
    public ResponseEntity<ApiResponse<List<String>>> getDaysAvailable(
        @Parameter(description = "Fecha de inicio YYYY-MM-DD (defecto: hoy)", example = "2026-05-12")
        @RequestParam(required = false) String from,

        @Parameter(description = "Ventana en días calendario, 1–90 (defecto: 30)", example = "30")
        @RequestParam(required = false, defaultValue = "30") int days) {

        log.info("GET /api/appointments/days-available - from={}, days={}", from, days);

        if (days < 1 || days > 90) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "El parámetro 'days' debe estar entre 1 y 90.",
                (String) null
            ));
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        LocalDate inicio;

        if (from == null || from.isBlank()) {
            inicio = hoy;
        } else {
            try {
                inicio = LocalDate.parse(from);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Formato de fecha inválido. Use YYYY-MM-DD.",
                    (String) null
                ));
            }
            if (inicio.isBefore(hoy)) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "La fecha de inicio no puede ser anterior a hoy.",
                    (String) null
                ));
            }
        }

        List<String> dias = appointmentService.getAvailableDays(inicio, days);
        ApiResponse<List<String>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Días disponibles obtenidos exitosamente",
            dias
        );
        return ResponseEntity.ok(response);
    }
}
