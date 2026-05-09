package cl.sgl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints públicos del flujo de agendamiento.
 * No requieren autenticación JWT.
 *
 * Rutas:
 * - GET /api/appointments/ping → health check del módulo de agendamiento
 *
 * Historia: SGL-016 AG-NOLOGIN
 */
@RestController
@RequestMapping("/api/appointments")
@Slf4j
@Tag(name = "Agendamiento público", description = "Endpoints públicos para el flujo de agendamiento de clientes")
public class PublicAppointmentController {

    @GetMapping("/ping")
    @Operation(
        summary = "Health check del módulo de agendamiento",
        description = "Confirma que el módulo de agendamiento público está disponible. No requiere autenticación."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Módulo disponible"
        )
    })
    public ResponseEntity<Map<String, String>> ping() {
        log.debug("GET /api/appointments/ping");
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Agendamiento público disponible"
        ));
    }
}
