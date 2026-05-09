package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.ServicePublicDTO;
import cl.sgl.service.LegalServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints públicos de servicios legales para el formulario de agendamiento.
 * No requieren autenticación JWT.
 *
 * Rutas:
 * - GET /api/services → lista de servicios activos
 *
 * Historia: SGL-018 AG-SELECT-MAT
 */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Servicios públicos", description = "Servicios legales disponibles para agendamiento — sin autenticación")
public class PublicServiceController {

    private final LegalServiceService legalServiceService;

    @GetMapping
    @Operation(
        summary = "Listar servicios disponibles",
        description = "Retorna los servicios legales activos para que el cliente pueda seleccionar la materia al agendar. No requiere autenticación."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Lista de servicios activos",
        content = @Content(schema = @Schema(implementation = ApiResponse.class))
    )
    public ResponseEntity<ApiResponse<List<ServicePublicDTO>>> getServices() {
        log.info("GET /api/services");

        List<ServicePublicDTO> services = legalServiceService.getPublicServices();
        ApiResponse<List<ServicePublicDTO>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Servicios obtenidos exitosamente",
            services
        );
        return ResponseEntity.ok(response);
    }
}
