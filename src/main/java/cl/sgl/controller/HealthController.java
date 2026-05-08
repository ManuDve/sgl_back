package cl.sgl.controller;

import cl.sgl.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador de salud de la aplicación
 */
@Slf4j
@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Endpoints de estado de la aplicación")
public class HealthController {

    @GetMapping
    @Operation(summary = "Verificar estado de la aplicación", 
               description = "Endpoint público sin autenticación que retorna el estado actual del servidor")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Aplicación está activa"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Aplicación no disponible")
    })
    public ApiResponse<Map<String, Object>> health() {
        log.info("Health check requested");
        
        Map<String, Object> healthData = new LinkedHashMap<>();
        healthData.put("status", "UP");
        healthData.put("service", "SGL Backend");
        healthData.put("version", "0.0.1-SNAPSHOT");
        
        return ApiResponse.success(healthData, "Service is healthy");
    }
}
