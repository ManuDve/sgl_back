package cl.sgl.controller;

import cl.sgl.config.JwtUtil;
import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.AppointmentDetailDTO;
import cl.sgl.dto.CreateAppointmentRequest;
import cl.sgl.dto.OtpRequest;
import cl.sgl.dto.RescheduleRequest;
import cl.sgl.dto.VerifyOtpRequest;
import cl.sgl.dto.VerifyOtpResponse;
import cl.sgl.exception.UnauthorizedException;
import cl.sgl.service.AppointmentService;
import cl.sgl.service.OtpService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * - GET  /api/appointments/{idExterno}                       → detalle público de agendamiento
 * - POST /api/appointments/{idExterno}/request-otp          → solicitar OTP de verificación
 * - GET  /api/appointments/ping                              → health check
 * - GET  /api/appointments/hours-available?date=...          → horas disponibles
 * - GET  /api/appointments/days-available?from=...&days=30   → días hábiles disponibles
 *
 * Historias: SGL-016 AG-NOLOGIN, SGL-021 AG-HORAS, SGL-020 AG-FECHAS, SGL-024 AG-IDEXTERNO,
 *            SGL-066 GES-OTP
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agendamiento público", description = "Endpoints públicos para el flujo de agendamiento de clientes")
public class PublicAppointmentController {

    private final AppointmentService appointmentService;
    private final OtpService         otpService;
    private final JwtUtil            jwtUtil;

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

    @GetMapping("/{idExterno}")
    @Operation(
        summary = "Detalle público de agendamiento",
        description = "Retorna el detalle de un agendamiento por su idExterno (AG-XXXX-NNNN). Usado en la pantalla de confirmación. No requiere autenticación."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Agendamiento encontrado",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Agendamiento no encontrado")
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> getByIdExterno(
        @Parameter(description = "ID externo en formato AG-XXXX-NNNN", example = "AG-ABCD-0001")
        @PathVariable String idExterno) {

        log.info("GET /api/appointments/{}", idExterno);
        AppointmentDetailDTO detail = appointmentService.getByIdExterno(idExterno);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Agendamiento obtenido exitosamente",
            detail
        ));
    }

    @PostMapping("/{idExterno}/request-otp")
    @Operation(
        summary = "Solicitar OTP de verificación",
        description = """
            Solicita un código OTP de 6 dígitos para verificar identidad antes de reagendar o cancelar.
            El cliente debe proporcionar el email O teléfono que usó al agendar.
            La respuesta es siempre la misma, independientemente de si la cita existe o si los datos
            coinciden, para evitar enumeración de citas.
            No requiere autenticación.
            """
    )
    public ResponseEntity<ApiResponse<Void>> requestOtp(
            @Parameter(description = "ID externo en formato AG-XXXX-NNNN", example = "AG-ABCD-0001")
            @PathVariable String idExterno,
            @Valid @RequestBody OtpRequest request) {

        log.info("POST /api/appointments/{}/request-otp", idExterno);
        otpService.requestOtp(idExterno, request);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Si la información coincide, recibirás un código en tu correo en los próximos minutos.",
            null
        ));
    }

    @PostMapping("/{idExterno}/verify-otp")
    @Operation(
        summary = "Verificar OTP y obtener token de gestión",
        description = """
            Valida el código OTP de 6 dígitos enviado al cliente. Si es correcto y no ha expirado,
            retorna un token JWT de corta vida (10 minutos) que autoriza reagendar o cancelar
            únicamente la cita indicada por idExterno.
            Si el OTP es inválido o ha expirado, retorna 401.
            No requiere autenticación.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "OTP válido — token de gestión emitido",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Body inválido (campo otp ausente o vacío)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "OTP incorrecto o expirado")
    })
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
            @Parameter(description = "ID externo en formato AG-XXXX-NNNN", example = "AG-ABCD-0001")
            @PathVariable String idExterno,
            @Valid @RequestBody VerifyOtpRequest request) {

        log.info("POST /api/appointments/{}/verify-otp", idExterno);

        if (!otpService.verifyOtp(idExterno, request.getOtp())) {
            throw new UnauthorizedException("El código OTP es inválido o ha expirado.");
        }

        String token = jwtUtil.generateManagementToken(idExterno);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Identidad verificada. Token válido por 10 minutos.",
            new VerifyOtpResponse(token, idExterno, 600)
        ));
    }

    @PatchMapping("/{idExterno}/reagendar")
    @Operation(
        summary = "Reagendar cita",
        description = """
            Cambia la fecha y hora de una cita existente.
            Requiere el token de gestión emitido por POST /verify-otp (Bearer en Authorization header).
            Políticas: no se puede reagendar con menos de 24h de anticipación, ni si el slot está ocupado,
            ni si la cita está CANCELLED. Si estaba CONFIRMED, el estado vuelve a PENDING.
            Historia: SGL-064 GES-REAG-WEB
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Cita reagendada exitosamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "Token de gestión ausente, inválido o no corresponde a esta cita"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Cita no encontrada"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "No se puede reagendar: cancelada, menos de 24h, o slot ocupado")
    })
    public ResponseEntity<ApiResponse<AppointmentDetailDTO>> reagendar(
            @Parameter(description = "ID externo en formato AG-XXXX-NNNN", example = "AG-ABCD-0001")
            @PathVariable String idExterno,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RescheduleRequest request) {

        log.info("PATCH /api/appointments/{}/reagendar", idExterno);

        String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
            ? authorizationHeader.substring(7)
            : null;

        if (token == null || !jwtUtil.isManagementTokenValid(token, idExterno)) {
            throw new UnauthorizedException("Token de gestión inválido o no autorizado para esta cita.");
        }

        AppointmentDetailDTO resultado = appointmentService.reschedule(idExterno, request);
        return ResponseEntity.ok(new ApiResponse<>(
            HttpStatus.OK.value(),
            "Cita reagendada exitosamente.",
            resultado
        ));
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
