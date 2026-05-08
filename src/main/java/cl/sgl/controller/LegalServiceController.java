package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import cl.sgl.dto.CreateLegalServiceRequest;
import cl.sgl.dto.LegalServiceResponse;
import cl.sgl.dto.UpdateLegalServiceRequest;
import cl.sgl.service.LegalServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestión de servicios legales.
 * Proporciona endpoints CRUD para administrar servicios.
 *
 * Rutas:
 * - POST   /api/admin/services           → Crear servicio
 * - GET    /api/admin/services           → Obtener todos los servicios
 * - GET    /api/admin/services/{id}      → Obtener servicio por ID
 * - PUT    /api/admin/services/{id}      → Actualizar servicio
 * - DELETE /api/admin/services/{id}      → Eliminar servicio
 * - GET    /api/public/services          → Obtener servicios activos (públicos)
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Servicios", description = "Gestión de servicios legales")
public class LegalServiceController {

    private final LegalServiceService legalServiceService;

    /**
     * Crea un nuevo servicio.
     * Solo para administrador.
     *
     * @param request DTO con los datos del servicio
     * @return Respuesta con el servicio creado
     */
    @PostMapping("/api/admin/services")
    @Operation(summary = "Crear nuevo servicio", description = "Crea un nuevo servicio legal. Requiere autenticación de administrador.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Servicio creado exitosamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validación fallida (nombre duplicado o campos inválidos)"
        )
    })
    public ResponseEntity<ApiResponse<LegalServiceResponse>> createService(
        @Valid @RequestBody CreateLegalServiceRequest request) {
        log.info("POST /api/admin/services - Crear servicio: {}", request.getName());

        try {
            LegalServiceResponse response = legalServiceService.createService(request);
            ApiResponse<LegalServiceResponse> apiResponse = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Servicio creado exitosamente",
                response
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al crear servicio: {}", e.getMessage());
            ApiResponse<LegalServiceResponse> errorResponse = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Error de validación",
                e.getMessage()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Obtiene todos los servicios (activos e inactivos).
     * Solo para administrador.
     *
     * @return Lista de todos los servicios
     */
    @GetMapping("/api/admin/services")
    @Operation(summary = "Obtener todos los servicios", description = "Retorna una lista de todos los servicios (activos e inactivos). Requiere autenticación de administrador.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Lista de servicios",
        content = @Content(schema = @Schema(implementation = ApiResponse.class))
    )
    public ResponseEntity<ApiResponse<List<LegalServiceResponse>>> getAllServices() {
        log.info("GET /api/admin/services - Obtener todos los servicios");

        List<LegalServiceResponse> services = legalServiceService.getAllServices();
        ApiResponse<List<LegalServiceResponse>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Servicios obtenidos exitosamente",
            services
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene un servicio por su ID.
     * Solo para administrador.
     *
     * @param id ID del servicio
     * @return Datos del servicio
     */
    @GetMapping("/api/admin/services/{id}")
    @Operation(summary = "Obtener servicio por ID", description = "Retorna los detalles de un servicio específico por su ID. Requiere autenticación de administrador.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Servicio encontrado",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Servicio no encontrado"
        )
    })
    public ResponseEntity<ApiResponse<LegalServiceResponse>> getServiceById(
        @PathVariable Long id) {
        log.info("GET /api/admin/services/{} - Obtener servicio por ID", id);

        try {
            LegalServiceResponse service = legalServiceService.getServiceById(id);
            ApiResponse<LegalServiceResponse> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio obtenido exitosamente",
                service
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al obtener servicio: {}", e.getMessage());
            ApiResponse<LegalServiceResponse> errorResponse = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    /**
     * Actualiza un servicio existente.
     * Solo para administrador.
     *
     * @param id ID del servicio a actualizar
     * @param request DTO con los datos a actualizar
     * @return Datos del servicio actualizado
     */
    @PutMapping("/api/admin/services/{id}")
    @Operation(summary = "Actualizar servicio", description = "Actualiza un servicio existente. Solo se actualizan los campos proporcionados. Requiere autenticación de administrador.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Servicio actualizado exitosamente",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Servicio no encontrado"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validación fallida"
        )
    })
    public ResponseEntity<ApiResponse<LegalServiceResponse>> updateService(
        @PathVariable Long id,
        @Valid @RequestBody UpdateLegalServiceRequest request) {
        log.info("PUT /api/admin/services/{} - Actualizar servicio", id);

        try {
            LegalServiceResponse response = legalServiceService.updateService(id, request);
            ApiResponse<LegalServiceResponse> apiResponse = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio actualizado exitosamente",
                response
            );
            return ResponseEntity.ok(apiResponse);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al actualizar servicio: {}", e.getMessage());
            ApiResponse<LegalServiceResponse> errorResponse = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "Error de validación",
                e.getMessage()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error al actualizar servicio: {}", e.getMessage());
            ApiResponse<LegalServiceResponse> errorResponse = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    /**
     * Elimina un servicio por su ID.
     * Solo para administrador.
     *
     * @param id ID del servicio a eliminar
     * @return Respuesta de confirmación
     */
    @DeleteMapping("/api/admin/services/{id}")
    @Operation(summary = "Eliminar servicio", description = "Elimina un servicio existente. Requiere autenticación de administrador.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Servicio eliminado exitosamente"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Servicio no encontrado"
        )
    })
    public ResponseEntity<ApiResponse<Void>> deleteService(
        @PathVariable Long id) {
        log.info("DELETE /api/admin/services/{} - Eliminar servicio", id);

        try {
            legalServiceService.deleteService(id);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Servicio eliminado exitosamente",
                null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al eliminar servicio: {}", e.getMessage());
            ApiResponse<Void> errorResponse = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
    }

    /**
     * Obtiene todos los servicios activos.
     * Endpoint público para el formulario de agendamiento.
     *
     * @return Lista de servicios activos
     */
    @GetMapping("/api/public/services")
    @Operation(summary = "Obtener servicios activos", description = "Retorna una lista de todos los servicios activos. Este endpoint es público para el formulario de agendamiento.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Lista de servicios activos",
        content = @Content(schema = @Schema(implementation = ApiResponse.class))
    )
    public ResponseEntity<ApiResponse<List<LegalServiceResponse>>> getActiveServices() {
        log.info("GET /api/public/services - Obtener servicios activos");

        List<LegalServiceResponse> services = legalServiceService.getActiveServices();
        ApiResponse<List<LegalServiceResponse>> response = new ApiResponse<>(
            HttpStatus.OK.value(),
            "Servicios obtenidos exitosamente",
            services
        );
        return ResponseEntity.ok(response);
    }
}
